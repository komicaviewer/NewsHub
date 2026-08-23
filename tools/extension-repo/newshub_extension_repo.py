#!/usr/bin/env python3
"""Build and validate a NewsHub trusted extension repository."""

from __future__ import annotations

import argparse
import base64
import copy
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

PROTOCOL_VERSION = 2
SPEC_VERSION = "1.0"
KEY_TYPE = "ecdsa"
KEY_SCHEME = "ecdsa-sha2-nistp256"
ROLES = ("root", "targets", "snapshot", "timestamp")
KNOWN_CAPABILITIES = {
    "resource_read",
    "external_link",
    "ptt_adult_consent_status",
    "eyny_challenge_proof",
}
SHA256_RE = re.compile(r"^[a-f0-9]{64}$")
PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$")
SOURCE_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,160}$")
SAFE_STEM_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class RepoError(Exception):
    pass


def strict_json(path: Path) -> dict[str, Any]:
    def pairs(values: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in values:
            if key in result:
                raise RepoError(f"duplicate JSON member: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=pairs,
            parse_float=lambda _: (_ for _ in ()).throw(RepoError("floats are forbidden")),
        )
    except (OSError, json.JSONDecodeError) as error:
        raise RepoError(f"cannot read JSON {path}: {error}") from error
    if not isinstance(value, dict):
        raise RepoError(f"expected a JSON object: {path}")
    return value


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def pretty_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def require_keys(value: dict[str, Any], keys: set[str], label: str) -> None:
    actual = set(value)
    if actual != keys:
        raise RepoError(f"{label} fields differ: missing={sorted(keys - actual)}, unknown={sorted(actual - keys)}")


def require_int(value: Any, label: str, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise RepoError(f"{label} must be an integer >= {minimum}")
    return value


def require_text(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise RepoError(f"{label} must be a non-empty string")
    return value


def require_sha256(value: Any, label: str) -> str:
    text = require_text(value, label)
    if not SHA256_RE.fullmatch(text):
        raise RepoError(f"{label} must be a lower-case SHA-256 digest")
    return text


def parse_expiry(value: Any, label: str, now: dt.datetime | None = None) -> dt.datetime:
    text = require_text(value, label)
    try:
        parsed = dt.datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as error:
        raise RepoError(f"{label} must be an RFC 3339 timestamp") from error
    if parsed.tzinfo is None:
        raise RepoError(f"{label} must include a UTC offset")
    if now is not None and parsed <= now:
        raise RepoError(f"{label} has expired")
    return parsed


def expiry(days: int) -> str:
    value = dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=days)
    return value.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def is_exact_host(host: Any) -> bool:
    if not isinstance(host, str) or host != host.lower() or host.endswith("."):
        return False
    if len(host) not in range(1, 254) or "*" in host or ":" in host:
        return False
    labels = host.split(".")
    if len(labels) < 2 or all(part.isdigit() for part in labels):
        return False
    return all(
        1 <= len(part) <= 63
        and part[0].isalnum()
        and part[-1].isalnum()
        and all(char.isalnum() or char == "-" for char in part)
        for part in labels
    )


def validate_repository_base_url(value: Any) -> str:
    text = require_text(value, "repository.baseUrl")
    parsed = urlparse(text)
    try:
        port = parsed.port
    except ValueError as error:
        raise RepoError("repository.baseUrl has an invalid port") from error
    host = parsed.hostname
    if (
        parsed.scheme != "https"
        or not host
        or not is_exact_host(host)
        or host not in parsed.netloc
        or parsed.username is not None
        or parsed.password is not None
        or port not in (None, 443)
        or parsed.query
        or parsed.fragment
        or "//" in parsed.path
        or any(segment in {".", ".."} for segment in parsed.path.split("/"))
        or "%" in parsed.netloc
    ):
        raise RepoError("repository.baseUrl must be a canonical HTTPS URL with an exact lower-case DNS host")
    return text


def validate_string_set(value: Any, label: str, *, minimum: int = 0, maximum: int = 32) -> list[str]:
    if not isinstance(value, list) or len(value) not in range(minimum, maximum + 1):
        raise RepoError(f"{label} must contain {minimum} to {maximum} values")
    if any(not isinstance(item, str) or not item for item in value) or len(set(value)) != len(value):
        raise RepoError(f"{label} must contain unique non-empty strings")
    return value


def validate_operation(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise RepoError(f"{label} must be an object")
    require_keys(value, {"name", "methods", "pathPrefixes", "credentialed"}, label)
    if value["name"] != "source_read":
        raise RepoError(f"{label}.name must be source_read")
    methods = validate_string_set(value["methods"], f"{label}.methods", minimum=1, maximum=2)
    if any(method not in {"GET", "HEAD"} for method in methods):
        raise RepoError(f"{label}.methods permits only GET and HEAD")
    prefixes = validate_string_set(value["pathPrefixes"], f"{label}.pathPrefixes", minimum=1, maximum=16)
    if any(len(item) > 256 or not item.startswith("/") or any(ord(char) < 32 or ord(char) == 127 for char in item) for item in prefixes):
        raise RepoError(f"{label}.pathPrefixes contains an invalid prefix")
    if not isinstance(value["credentialed"], bool):
        raise RepoError(f"{label}.credentialed must be boolean")
    return value


def validate_network_policy(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise RepoError("networkPolicy must be an object")
    require_keys(value, {"schemaVersion", "request", "resource", "external", "auth", "namedCapabilities"}, "networkPolicy")
    version = value["schemaVersion"]
    if version not in {2, 3}:
        raise RepoError("networkPolicy.schemaVersion must be 2 or 3")
    for scope in ("external", "auth"):
        owner = value[scope]
        if not isinstance(owner, dict):
            raise RepoError(f"networkPolicy.{scope} must be an object")
        require_keys(owner, {"exactHosts"}, f"networkPolicy.{scope}")
        hosts = validate_string_set(owner["exactHosts"], f"networkPolicy.{scope}.exactHosts")
        if any(not is_exact_host(host) for host in hosts):
            raise RepoError(f"networkPolicy.{scope}.exactHosts requires canonical DNS hosts")
    resource = value["resource"]
    if not isinstance(resource, dict):
        raise RepoError("networkPolicy.resource must be an object")
    resource_hosts: set[str] = set()
    if version == 2:
        require_keys(resource, {"exactHosts"}, "networkPolicy.resource")
        hosts = validate_string_set(resource["exactHosts"], "networkPolicy.resource.exactHosts")
        if any(not is_exact_host(host) for host in hosts):
            raise RepoError("networkPolicy.resource.exactHosts requires canonical DNS hosts")
        resource_hosts.update(hosts)
    else:
        require_keys(resource, {"rules"}, "networkPolicy.resource")
        resource_rules = resource["rules"]
        if not isinstance(resource_rules, list) or len(resource_rules) > 32:
            raise RepoError("networkPolicy.resource.rules must contain at most 32 rules")
        seen_resource_hosts: set[str] = set()
        for index, rule in enumerate(resource_rules):
            label = f"networkPolicy.resource.rules[{index}]"
            if not isinstance(rule, dict):
                raise RepoError(f"{label} must be an object")
            require_keys(rule, {"exactHosts", "exactPaths", "pathPrefixes", "credentialed", "userAgent"}, label)
            hosts = validate_string_set(rule["exactHosts"], f"{label}.exactHosts", minimum=1)
            if any(not is_exact_host(host) for host in hosts):
                raise RepoError(f"{label}.exactHosts requires canonical DNS hosts")
            overlap = seen_resource_hosts.intersection(hosts)
            if overlap:
                raise RepoError(f"{label}.exactHosts overlaps another resource rule")
            seen_resource_hosts.update(hosts)
            prefixes = validate_string_set(rule["pathPrefixes"], f"{label}.pathPrefixes", minimum=1, maximum=16)
            if any(
                len(item) > 256 or not item.startswith("/") or
                any(ord(char) < 32 or ord(char) == 127 for char in item)
                for item in prefixes
            ):
                raise RepoError(f"{label}.pathPrefixes contains an invalid prefix")
            exact_paths = validate_string_set(rule["exactPaths"], f"{label}.exactPaths", maximum=16)
            if len(prefixes) + len(exact_paths) > 16 or any(
                len(item) > 256 or not item.startswith("/") or
                any(ord(char) < 32 or ord(char) == 127 for char in item)
                for item in exact_paths
            ):
                raise RepoError(f"{label}.exactPaths contains an invalid path")
            credentialed = rule["credentialed"]
            if not isinstance(credentialed, bool):
                raise RepoError(f"{label}.credentialed must be boolean")
            user_agent = rule["userAgent"]
            valid_user_agent = (
                isinstance(user_agent, str)
                and user_agent == user_agent.strip()
                and 1 <= len(user_agent) <= 512
                and all(32 <= ord(char) != 127 for char in user_agent)
            )
            if credentialed != valid_user_agent:
                raise RepoError(f"{label} credentials require an exact valid userAgent")
            if not credentialed and user_agent is not None:
                raise RepoError(f"{label}.userAgent must be null for public resources")
            resource_hosts.update(hosts)
    request = value["request"]
    if not isinstance(request, dict):
        raise RepoError("networkPolicy.request must be an object")
    require_keys(request, {"rules"}, "networkPolicy.request")
    rules = request["rules"]
    if not isinstance(rules, list) or len(rules) not in range(1, 33):
        raise RepoError("networkPolicy.request.rules must contain 1 to 32 rules")
    seen_rules: set[bytes] = set()
    request_hosts: set[str] = set()
    for index, rule in enumerate(rules):
        label = f"networkPolicy.request.rules[{index}]"
        if not isinstance(rule, dict):
            raise RepoError(f"{label} must be an object")
        require_keys(rule, {"exactHosts", "operation"}, label)
        hosts = validate_string_set(rule["exactHosts"], f"{label}.exactHosts", minimum=1)
        if any(not is_exact_host(host) for host in hosts):
            raise RepoError(f"{label}.exactHosts requires canonical DNS hosts")
        validate_operation(rule["operation"], f"{label}.operation")
        encoded = canonical_bytes(rule)
        if encoded in seen_rules:
            raise RepoError("networkPolicy.request.rules contains a duplicate")
        seen_rules.add(encoded)
        request_hosts.update(hosts)
    all_hosts = request_hosts.copy()
    all_hosts.update(resource_hosts)
    for scope in ("external", "auth"):
        all_hosts.update(value[scope]["exactHosts"])
    if len(request_hosts) > 32 or len(all_hosts) > 32:
        raise RepoError("networkPolicy contains more than 32 hosts")
    credentialed_resource_hosts = {
        host
        for rule in value["resource"].get("rules", [])
        if rule["credentialed"]
        for host in rule["exactHosts"]
    }
    if not credentialed_resource_hosts.issubset(set(value["auth"]["exactHosts"])):
        raise RepoError("credentialed resource hosts must be inside networkPolicy.auth.exactHosts")
    capabilities = validate_string_set(value["namedCapabilities"], "networkPolicy.namedCapabilities", maximum=16)
    unknown = set(capabilities) - KNOWN_CAPABILITIES
    if unknown:
        raise RepoError(f"networkPolicy has unknown capabilities: {sorted(unknown)}")
    return value


def network_policy_hash(policy: dict[str, Any]) -> str:
    validate_network_policy(policy)
    if policy["schemaVersion"] == 2:
        # Preserve the already-published v2 producer contract byte-for-byte.
        return sha256(canonical_bytes(policy))
    normalized = json.loads(json.dumps(policy))
    normalized["namedCapabilities"] = sorted(normalized["namedCapabilities"])
    for scope in ("external", "auth"):
        normalized[scope]["exactHosts"] = sorted(normalized[scope]["exactHosts"])
    request_rules = normalized["request"]["rules"]
    for rule in request_rules:
        rule["exactHosts"] = sorted(rule["exactHosts"])
        rule["operation"]["methods"] = sorted(rule["operation"]["methods"])
        rule["operation"]["pathPrefixes"] = sorted(rule["operation"]["pathPrefixes"])
    request_rules.sort(key=canonical_bytes)
    resource_rules = normalized["resource"]["rules"]
    for rule in resource_rules:
        rule["exactHosts"] = sorted(rule["exactHosts"])
        rule["exactPaths"] = sorted(rule["exactPaths"])
        rule["pathPrefixes"] = sorted(rule["pathPrefixes"])
    resource_rules.sort(key=canonical_bytes)
    return sha256(canonical_bytes(normalized))


def validate_source(source: Any, label: str) -> dict[str, Any]:
    if not isinstance(source, dict):
        raise RepoError(f"{label} must be an object")
    require_keys(source, {"id", "name", "lang", "baseUrl", "service", "protocol", "networkPolicy"}, label)
    if not SOURCE_ID_RE.fullmatch(require_text(source["id"], f"{label}.id")):
        raise RepoError(f"{label}.id is invalid")
    for field in ("name", "lang", "service"):
        require_text(source[field], f"{label}.{field}")
    if source["protocol"] != PROTOCOL_VERSION:
        raise RepoError(f"{label}.protocol must be {PROTOCOL_VERSION}")
    parsed = urlparse(require_text(source["baseUrl"], f"{label}.baseUrl"))
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.port not in (None, 443):
        raise RepoError(f"{label}.baseUrl must be an HTTPS origin or path without credentials, query, or fragment")
    policy = validate_network_policy(source["networkPolicy"])
    hosts = {host for rule in policy["request"]["rules"] for host in rule["exactHosts"]}
    hosts.update(policy[scope]["exactHosts"] for scope in ())
    resource_hosts = (
        set(policy["resource"]["exactHosts"])
        if policy["schemaVersion"] == 2
        else {host for rule in policy["resource"]["rules"] for host in rule["exactHosts"]}
    )
    all_hosts = hosts | resource_hosts | set(policy["external"]["exactHosts"]) | set(policy["auth"]["exactHosts"])
    if parsed.hostname.lower() not in all_hosts:
        raise RepoError(f"{label}.baseUrl host is outside networkPolicy")
    return source


def validate_accepted_artifacts(value: Any, current_version: int, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or len(value) > 2:
        raise RepoError(f"{label} must contain at most two artifacts")
    versions: set[int] = set()
    triples: set[tuple[int, int, str]] = set()
    for index, artifact in enumerate(value):
        item_label = f"{label}[{index}]"
        if not isinstance(artifact, dict):
            raise RepoError(f"{item_label} must be an object")
        require_keys(artifact, {"versionCode", "length", "sha256"}, item_label)
        version = require_int(artifact["versionCode"], f"{item_label}.versionCode")
        length = require_int(artifact["length"], f"{item_label}.length")
        digest = require_sha256(artifact["sha256"], f"{item_label}.sha256")
        if version >= current_version:
            raise RepoError(f"{item_label}.versionCode must be lower than the current version")
        if length > 64 * 1024 * 1024:
            raise RepoError(f"{item_label}.length exceeds 64 MiB")
        triple = (version, length, digest)
        if version in versions or triple in triples:
            raise RepoError(f"{label} contains a duplicate version or artifact")
        versions.add(version)
        triples.add(triple)
    return value


def validate_config(config: dict[str, Any], config_path: Path) -> dict[str, Any]:
    require_keys(config, {"schemaVersion", "mode", "repository", "paths", "versions", "expiryDays", "roles", "extensions"}, "repository config")
    if config["schemaVersion"] != 1:
        raise RepoError("repository config schemaVersion must be 1")
    if config["mode"] not in {"ephemeral", "production"}:
        raise RepoError("repository config mode must be ephemeral or production")
    repository = config["repository"]
    if not isinstance(repository, dict):
        raise RepoError("repository must be an object")
    require_keys(repository, {"name", "description", "baseUrl", "iconUrl", "website"}, "repository")
    for field in ("name", "description", "baseUrl"):
        require_text(repository[field], f"repository.{field}")
    validate_repository_base_url(repository["baseUrl"])
    paths = config["paths"]
    if not isinstance(paths, dict):
        raise RepoError("paths must be an object")
    require_keys(paths, {"keyDirectory", "publicDirectory"}, "paths")
    key_dir = (config_path.parent / require_text(paths["keyDirectory"], "paths.keyDirectory")).resolve()
    public_dir = (config_path.parent / require_text(paths["publicDirectory"], "paths.publicDirectory")).resolve()
    if key_dir == public_dir or key_dir in public_dir.parents or public_dir in key_dir.parents:
        raise RepoError("private keys and public output must use separate directory trees")
    if not isinstance(config["versions"], dict) or not isinstance(config["expiryDays"], dict):
        raise RepoError("versions and expiryDays must be objects")
    if not isinstance(config["roles"], dict):
        raise RepoError("roles must be an object")
    for name in ("root", "timestamp", "snapshot", "targets"):
        require_int(config["versions"].get(name), f"versions.{name}")
        require_int(config["expiryDays"].get(name), f"expiryDays.{name}")
        role = config["roles"].get(name)
        if not isinstance(role, dict):
            raise RepoError(f"roles.{name} must be an object")
        require_keys(role, {"keyNames", "threshold"}, f"roles.{name}")
        names = validate_string_set(role["keyNames"], f"roles.{name}.keyNames", minimum=1)
        if any(not SAFE_STEM_RE.fullmatch(key_name) for key_name in names):
            raise RepoError(f"roles.{name}.keyNames contains an unsafe file stem")
        threshold = require_int(role["threshold"], f"roles.{name}.threshold")
        if threshold > len(names):
            raise RepoError(f"roles.{name}.threshold exceeds its key count")
    extensions = config["extensions"]
    if not isinstance(extensions, list) or not extensions:
        raise RepoError("extensions must contain at least one extension")
    package_names: set[str] = set()
    source_ids: set[str] = set()
    for index, extension in enumerate(extensions):
        label = f"extensions[{index}]"
        if not isinstance(extension, dict):
            raise RepoError(f"{label} must be an object")
        allowed = {"apk", "name", "lang", "apkSignerPins", "lineageRootSha256", "sources", "acceptedArtifacts"}
        unknown = set(extension) - allowed
        missing = allowed - {"acceptedArtifacts"} - set(extension)
        if unknown or missing:
            raise RepoError(f"{label} fields differ: missing={sorted(missing)}, unknown={sorted(unknown)}")
        require_text(extension["apk"], f"{label}.apk")
        for field in ("name", "lang"):
            require_text(extension[field], f"{label}.{field}")
        pins = validate_string_set(extension["apkSignerPins"], f"{label}.apkSignerPins", minimum=1)
        if any(not SHA256_RE.fullmatch(pin) for pin in pins):
            raise RepoError(f"{label}.apkSignerPins contains an invalid digest")
        require_sha256(extension["lineageRootSha256"], f"{label}.lineageRootSha256")
        sources = extension["sources"]
        if not isinstance(sources, list) or not sources:
            raise RepoError(f"{label}.sources must not be empty")
        for source_index, source in enumerate(sources):
            validate_source(source, f"{label}.sources[{source_index}]")
            if source["id"] in source_ids:
                raise RepoError(f"duplicate Source id: {source['id']}")
            source_ids.add(source["id"])
        apk_path = (config_path.parent / extension["apk"]).resolve()
        if not apk_path.is_file():
            raise RepoError(f"APK does not exist: {apk_path}")
    return config


def password_for(mode: str) -> bytes | None:
    value = os.environ.get("NEWSHUB_REPO_KEY_PASSWORD")
    if mode == "production" and not value:
        raise RepoError("production mode requires NEWSHUB_REPO_KEY_PASSWORD in the process environment")
    return value.encode("utf-8") if value else None


def ensure_private_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path, 0o700)
    if stat.S_IMODE(path.stat().st_mode) != 0o700:
        raise RepoError(f"private key directory is not mode 0700: {path}")


def write_private_key(path: Path, key: ec.EllipticCurvePrivateKey, password: bytes | None) -> None:
    encryption = serialization.BestAvailableEncryption(password) if password else serialization.NoEncryption()
    data = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, encryption)
    path.write_bytes(data)
    os.chmod(path, 0o600)


def load_private_key(path: Path, password: bytes | None) -> ec.EllipticCurvePrivateKey:
    if not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise RepoError(f"private key must exist with mode 0600: {path}")
    try:
        value = serialization.load_pem_private_key(path.read_bytes(), password=password)
    except (ValueError, TypeError) as error:
        raise RepoError(f"cannot unlock private key {path}") from error
    if not isinstance(value, ec.EllipticCurvePrivateKey) or not isinstance(value.curve, ec.SECP256R1):
        raise RepoError(f"private key is not P-256: {path}")
    return value


def public_record(key: ec.EllipticCurvePrivateKey | ec.EllipticCurvePublicKey) -> tuple[str, dict[str, Any]]:
    public = key.public_key() if isinstance(key, ec.EllipticCurvePrivateKey) else key
    encoded = public.public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    key_id = sha256(encoded)
    return key_id, {
        "keytype": KEY_TYPE,
        "scheme": KEY_SCHEME,
        "keyval": {"public": base64.b64encode(encoded).decode("ascii")},
    }


def key_path(config_path: Path, config: dict[str, Any], name: str) -> Path:
    return (config_path.parent / config["paths"]["keyDirectory"] / f"{name}.pem").resolve()


def load_role_keys(config_path: Path, config: dict[str, Any], role: str) -> list[ec.EllipticCurvePrivateKey]:
    password = password_for(config["mode"])
    return [load_private_key(key_path(config_path, config, name), password) for name in config["roles"][role]["keyNames"]]


def sign_envelope(signed: dict[str, Any], keys: list[ec.EllipticCurvePrivateKey]) -> dict[str, Any]:
    payload = canonical_bytes(signed)
    signatures = []
    for key in keys:
        key_id, _ = public_record(key)
        signature = key.sign(payload, ec.ECDSA(hashes.SHA256()))
        signatures.append({"keyid": key_id, "sig": base64.b64encode(signature).decode("ascii")})
    return {"signatures": signatures, "signed": signed}


def build_root(config_path: Path, config: dict[str, Any], version: int | None = None) -> dict[str, Any]:
    role_keys = {role: load_role_keys(config_path, config, role) for role in ROLES}
    records: dict[str, Any] = {}
    roles: dict[str, Any] = {}
    for role, keys in role_keys.items():
        ids = []
        for key in keys:
            key_id, record = public_record(key)
            records[key_id] = record
            ids.append(key_id)
        roles[role] = {"keyids": ids, "threshold": config["roles"][role]["threshold"]}
    signed = {
        "_type": "root",
        "consistentSnapshot": True,
        "expires": expiry(config["expiryDays"]["root"]),
        "keys": records,
        "roles": roles,
        "specVersion": SPEC_VERSION,
        "version": version or config["versions"]["root"],
    }
    return sign_envelope(signed, role_keys["root"])


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as handle:
        handle.write(data)
        temporary = Path(handle.name)
    os.replace(temporary, path)


def write_versioned(path: Path, data: bytes) -> None:
    if path.exists() and path.read_bytes() != data:
        raise RepoError(f"refusing same-version metadata replacement: {path}")
    write_atomic(path, data)


def locate_android_tool(name: str, config_path: Path | None = None) -> str:
    found = shutil.which(name)
    if found:
        return found
    roots = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")]
    if config_path:
        for parent in (config_path.parent, *config_path.parents):
            local = parent / "local.properties"
            if local.is_file():
                for line in local.read_text(encoding="utf-8").splitlines():
                    if line.startswith("sdk.dir="):
                        roots.append(line.split("=", 1)[1].replace("\\:", ":"))
                break
    candidates: list[Path] = []
    for root in filter(None, roots):
        base = Path(root)
        if name == "apkanalyzer":
            candidates.extend([base / "cmdline-tools/latest/bin/apkanalyzer", base / "tools/bin/apkanalyzer"])
        else:
            candidates.extend(sorted((base / "build-tools").glob(f"*/{name}"), reverse=True))
    for candidate in candidates:
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    raise RepoError(f"cannot find Android SDK tool: {name}")


def run_tool(arguments: list[str]) -> str:
    result = subprocess.run(arguments, capture_output=True, text=True, timeout=60, check=False)
    if result.returncode:
        raise RepoError(f"command failed ({' '.join(arguments[:2])}): {(result.stderr or result.stdout).strip()}")
    return result.stdout


def inspect_apk(apk: Path, config_path: Path) -> dict[str, Any]:
    signer_output = run_tool([locate_android_tool("apksigner", config_path), "verify", "--print-certs", str(apk)])
    digests = [match.lower().replace(":", "") for match in re.findall(r"certificate SHA-256 digest:\s*([0-9A-Fa-f:]{64,95})", signer_output)]
    if not digests:
        raise RepoError(f"APK has no verified signer: {apk}")
    manifest_text = run_tool([locate_android_tool("apkanalyzer", config_path), "manifest", "print", str(apk)])
    try:
        manifest = ET.fromstring(manifest_text)
    except ET.ParseError as error:
        raise RepoError(f"cannot parse APK manifest: {apk}") from error
    package_name = manifest.attrib.get("package")
    if not package_name or not PACKAGE_RE.fullmatch(package_name):
        raise RepoError("APK package name is invalid")
    version_code = require_int(int(manifest.attrib.get(ANDROID_NS + "versionCode", "0")), "APK versionCode")
    version_name = manifest.attrib.get(ANDROID_NS + "versionName")
    if not version_name or not SAFE_STEM_RE.fullmatch(version_name):
        raise RepoError("APK versionName must match [A-Za-z0-9][A-Za-z0-9._-]{0,79}")
    forbidden = ["uses-permission", "permission", "activity", "receiver", "provider", "instrumentation"]
    if any(manifest.findall(f".//{name}") for name in forbidden):
        raise RepoError("extension APK requests permissions or declares forbidden components")
    application = manifest.find("application")
    if application is None:
        raise RepoError("APK application is missing")
    services: dict[str, dict[str, Any]] = {}
    for service in application.findall("service"):
        name = service.attrib.get(ANDROID_NS + "name", "")
        if name.startswith("."):
            name = package_name + name
        if service.attrib.get(ANDROID_NS + "exported") != "true":
            raise RepoError(f"service is not exported: {name}")
        if service.attrib.get(ANDROID_NS + "isolatedProcess") != "true":
            raise RepoError(f"service is not isolated: {name}")
        if service.attrib.get(ANDROID_NS + "externalService") == "true":
            raise RepoError(f"service cannot use externalService: {name}")
        if service.attrib.get(ANDROID_NS + "permission") != "tw.kevinzhang.newshub.permission.BIND_EXTENSION":
            raise RepoError(f"service has the wrong bind permission: {name}")
        process = service.attrib.get(ANDROID_NS + "process", "")
        if not process.startswith(":"):
            raise RepoError(f"service must use a private process: {name}")
        actions = {node.attrib.get(ANDROID_NS + "name") for node in service.findall("intent-filter/action")}
        if "tw.kevinzhang.newshub.extension.SERVICE" not in actions:
            raise RepoError(f"service action is missing: {name}")
        metadata = {node.attrib.get(ANDROID_NS + "name"): node.attrib.get(ANDROID_NS + "value") for node in service.findall("meta-data")}
        services[name] = {"process": process, "metadata": metadata}
    if not services:
        raise RepoError("extension APK has no Source service")
    if len({value["process"] for value in services.values()}) != len(services):
        raise RepoError("each Source service needs a distinct private process")
    return {
        "packageName": package_name,
        "versionCode": version_code,
        "versionName": version_name,
        "signerDigests": digests,
        "services": services,
    }


def verify_apk_contract(apk: Path, custom: dict[str, Any], config_path: Path) -> dict[str, Any]:
    inspected = inspect_apk(apk, config_path)
    if inspected["packageName"] != custom["packageName"] or inspected["versionCode"] != custom["versionCode"] or inspected["versionName"] != custom["versionName"]:
        raise RepoError(f"APK identity does not match targets metadata: {apk}")
    if inspected["signerDigests"][0] not in custom["apkSignerPins"]:
        raise RepoError(f"APK current signer is not pinned: {apk}")
    if custom["lineageRootSha256"] not in inspected["signerDigests"]:
        raise RepoError(f"APK signer lineage root is not present: {apk}")
    expected_services = {source["service"]: source for source in custom["sources"]}
    if set(inspected["services"]) != set(expected_services):
        raise RepoError(f"APK services do not match targets metadata: {apk}")
    key_map = {
        "newshub.extension.protocol": "protocol",
        "newshub.extension.source_id": "id",
        "newshub.extension.source_name": "name",
        "newshub.extension.source_lang": "lang",
        "newshub.extension.source_base_url": "baseUrl",
    }
    for service_name, expected in expected_services.items():
        metadata = inspected["services"][service_name]["metadata"]
        for manifest_key, target_key in key_map.items():
            actual = metadata.get(manifest_key)
            wanted = str(expected[target_key])
            if actual != wanted:
                raise RepoError(f"{service_name} metadata {manifest_key} is {actual!r}, expected {wanted!r}")
    return inspected


def target_entry(config_path: Path, extension: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    apk = (config_path.parent / extension["apk"]).resolve()
    inspected = inspect_apk(apk, config_path)
    pins = [require_sha256(pin, "apkSignerPins") for pin in extension["apkSignerPins"]]
    if inspected["signerDigests"][0] not in pins:
        raise RepoError(f"configured signer pins omit the APK current signer: {apk}")
    lineage = require_sha256(extension["lineageRootSha256"], "lineageRootSha256")
    if lineage not in inspected["signerDigests"]:
        raise RepoError(f"configured lineage root is absent from the APK signing history: {apk}")
    data = apk.read_bytes()
    target_name = f"apk/{inspected['packageName']}-v{inspected['versionName']}.apk"
    sources = []
    for source in extension["sources"]:
        validated = validate_source(source, f"Source {source.get('id', '')}")
        sources.append({
            "id": validated["id"],
            "name": validated["name"],
            "lang": validated["lang"],
            "baseUrl": validated["baseUrl"],
            "service": validated["service"],
            "protocol": validated["protocol"],
            "networkPolicy": validated["networkPolicy"],
            "policyHash": network_policy_hash(validated["networkPolicy"]),
        })
    custom = {
        "packageName": inspected["packageName"],
        "name": extension["name"],
        "versionCode": inspected["versionCode"],
        "versionName": inspected["versionName"],
        "lang": extension["lang"],
        "apkSignerPins": pins,
        "lineageRootSha256": lineage,
        "sources": sources,
    }
    if "acceptedArtifacts" in extension:
        custom["acceptedArtifacts"] = validate_accepted_artifacts(
            extension["acceptedArtifacts"],
            inspected["versionCode"],
            "acceptedArtifacts",
        )
    verify_apk_contract(apk, custom, config_path)
    return target_name, {"length": len(data), "hashes": {"sha256": sha256(data)}, "custom": custom, "_apk": apk}


def descriptor(data: bytes, version: int) -> dict[str, Any]:
    return {"version": version, "length": len(data), "hashes": {"sha256": sha256(data)}}


def output_directory(config_path: Path, config: dict[str, Any]) -> Path:
    return (config_path.parent / config["paths"]["publicDirectory"]).resolve()


def command_init(args: argparse.Namespace) -> None:
    config_path = args.config.resolve()
    if config_path.exists():
        raise RepoError(f"config already exists: {config_path}")
    key_dir = (config_path.parent / args.key_directory).resolve()
    public_dir = (config_path.parent / args.public_directory).resolve()
    if key_dir == public_dir or key_dir in public_dir.parents or public_dir in key_dir.parents:
        raise RepoError("private keys and public output must use separate directory trees")
    ensure_private_directory(key_dir)
    password = password_for(args.mode)
    role_names = {
        "root": ["root-1", "root-2", "root-3"],
        "targets": ["targets-1", "targets-2"],
        "snapshot": ["snapshot-1"],
        "timestamp": ["timestamp-1"],
    }
    for names in role_names.values():
        for name in names:
            path = key_dir / f"{name}.pem"
            if path.exists():
                raise RepoError(f"key already exists: {path}")
            write_private_key(path, ec.generate_private_key(ec.SECP256R1()), password)
    config = {
        "schemaVersion": 1,
        "mode": args.mode,
        "repository": {
            "name": "My NewsHub extensions",
            "description": "Extensions maintained by your_name_here",
            "baseUrl": "https://extensions.example.com",
            "iconUrl": None,
            "website": None,
        },
        "paths": {"keyDirectory": args.key_directory, "publicDirectory": args.public_directory},
        "versions": {"root": 1, "timestamp": 1, "snapshot": 1, "targets": 1},
        "expiryDays": {"root": 365, "timestamp": 2, "snapshot": 7, "targets": 30},
        "roles": {
            "root": {"keyNames": role_names["root"], "threshold": 2},
            "targets": {"keyNames": role_names["targets"], "threshold": 2},
            "snapshot": {"keyNames": role_names["snapshot"], "threshold": 1},
            "timestamp": {"keyNames": role_names["timestamp"], "threshold": 1},
        },
        "extensions": [],
    }
    write_atomic(config_path, pretty_bytes(config))
    ignore = key_dir / ".gitignore"
    write_atomic(ignore, b"*\n!.gitignore\n")
    os.chmod(ignore, 0o600)
    print(f"created {config_path}")
    print(f"created private keys in {key_dir} ({'encrypted' if password else 'unencrypted ephemeral keys'})")


def command_publish(args: argparse.Namespace) -> None:
    config_path = args.config.resolve()
    config = validate_config(strict_json(config_path), config_path)
    output = output_directory(config_path, config)
    metadata = output / "metadata"
    target_dir = output / "targets"
    metadata.mkdir(parents=True, exist_ok=True)
    target_dir.mkdir(parents=True, exist_ok=True)
    root_path = metadata / "root.json"
    if not root_path.exists():
        root = build_root(config_path, config)
        root_bytes = canonical_bytes(root)
        write_atomic(root_path, root_bytes)
        write_versioned(metadata / f"{config['versions']['root']}.root.json", root_bytes)
    else:
        root = strict_json(root_path)
        verify_root(root)
    targets: dict[str, Any] = {}
    for extension in config["extensions"]:
        path, entry = target_entry(config_path, extension)
        apk = entry.pop("_apk")
        targets[path] = entry
        write_atomic(target_dir / path, apk.read_bytes())
    repository = {key: value for key, value in config["repository"].items() if value is not None}
    targets_signed = {
        "_type": "targets",
        "expires": expiry(config["expiryDays"]["targets"]),
        "specVersion": SPEC_VERSION,
        "version": config["versions"]["targets"],
        "custom": {"repository": repository},
        "targets": targets,
    }
    targets_envelope = sign_envelope(targets_signed, load_role_keys(config_path, config, "targets"))
    targets_bytes = canonical_bytes(targets_envelope)
    snapshot_signed = {
        "_type": "snapshot",
        "expires": expiry(config["expiryDays"]["snapshot"]),
        "meta": {"targets.json": descriptor(targets_bytes, config["versions"]["targets"])},
        "specVersion": SPEC_VERSION,
        "version": config["versions"]["snapshot"],
    }
    snapshot_envelope = sign_envelope(snapshot_signed, load_role_keys(config_path, config, "snapshot"))
    snapshot_bytes = canonical_bytes(snapshot_envelope)
    timestamp_signed = {
        "_type": "timestamp",
        "expires": expiry(config["expiryDays"]["timestamp"]),
        "meta": {"snapshot.json": descriptor(snapshot_bytes, config["versions"]["snapshot"])},
        "specVersion": SPEC_VERSION,
        "version": config["versions"]["timestamp"],
    }
    timestamp_bytes = canonical_bytes(sign_envelope(timestamp_signed, load_role_keys(config_path, config, "timestamp")))
    write_versioned(metadata / f"{config['versions']['targets']}.targets.json", targets_bytes)
    write_versioned(metadata / f"{config['versions']['snapshot']}.snapshot.json", snapshot_bytes)
    write_atomic(metadata / "timestamp.json", timestamp_bytes)
    validate_repository(output, config_path)
    print(f"published and validated {output}")


def decode_public(record: dict[str, Any], key_id: str) -> ec.EllipticCurvePublicKey:
    require_keys(record, {"keytype", "scheme", "keyval"}, f"key {key_id}")
    if record["keytype"] != KEY_TYPE or record["scheme"] != KEY_SCHEME:
        raise RepoError(f"key {key_id} has an unsupported type")
    try:
        encoded = base64.b64decode(record["keyval"]["public"], validate=True)
        if sha256(encoded) != key_id:
            raise RepoError(f"key id does not match public key: {key_id}")
        key = serialization.load_der_public_key(encoded)
    except (KeyError, ValueError, TypeError) as error:
        raise RepoError(f"invalid public key: {key_id}") from error
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise RepoError(f"key is not P-256: {key_id}")
    return key


def parse_root(root: dict[str, Any]) -> tuple[dict[str, ec.EllipticCurvePublicKey], dict[str, Any]]:
    require_keys(root, {"signatures", "signed"}, "metadata envelope")
    signed = root["signed"]
    if not isinstance(signed, dict):
        raise RepoError("root.signed must be an object")
    require_keys(signed, {"_type", "consistentSnapshot", "expires", "keys", "roles", "specVersion", "version"}, "root.signed")
    if signed["_type"] != "root" or signed["specVersion"] != SPEC_VERSION or signed["consistentSnapshot"] is not True:
        raise RepoError("unsupported root metadata")
    require_int(signed["version"], "root.version")
    parse_expiry(signed["expires"], "root.expires", dt.datetime.now(dt.timezone.utc))
    keys = {key_id: decode_public(record, key_id) for key_id, record in signed["keys"].items()}
    if not keys or len(keys) > 64:
        raise RepoError("root must contain 1 to 64 keys")
    roles = signed["roles"]
    if set(roles) != set(ROLES):
        raise RepoError("root must define exactly four roles")
    for role in ROLES:
        record = roles[role]
        require_keys(record, {"keyids", "threshold"}, f"root role {role}")
        ids = validate_string_set(record["keyids"], f"root role {role} keyids", minimum=1)
        threshold = require_int(record["threshold"], f"root role {role} threshold")
        if threshold > len(ids) or any(key_id not in keys for key_id in ids):
            raise RepoError(f"root role {role} is invalid")
    return keys, roles


def verify_envelope(envelope: dict[str, Any], keys: dict[str, ec.EllipticCurvePublicKey], role: dict[str, Any], expected_type: str) -> dict[str, Any]:
    require_keys(envelope, {"signatures", "signed"}, f"{expected_type} envelope")
    signed = envelope["signed"]
    if not isinstance(signed, dict) or signed.get("_type") != expected_type or signed.get("specVersion") != SPEC_VERSION:
        raise RepoError(f"expected {expected_type} metadata")
    require_int(signed.get("version"), f"{expected_type}.version")
    parse_expiry(signed.get("expires"), f"{expected_type}.expires", dt.datetime.now(dt.timezone.utc))
    signatures = envelope["signatures"]
    if not isinstance(signatures, list) or len(signatures) not in range(1, 33):
        raise RepoError(f"{expected_type} has an invalid signature count")
    accepted: set[str] = set()
    payload = canonical_bytes(signed)
    for signature in signatures:
        if not isinstance(signature, dict) or set(signature) != {"keyid", "sig"}:
            raise RepoError(f"{expected_type} has a malformed signature")
        key_id = signature["keyid"]
        if key_id in accepted or key_id not in role["keyids"] or key_id not in keys:
            continue
        try:
            raw = base64.b64decode(signature["sig"], validate=True)
            keys[key_id].verify(raw, payload, ec.ECDSA(hashes.SHA256()))
            accepted.add(key_id)
        except (ValueError, InvalidSignature):
            continue
    if len(accepted) < role["threshold"]:
        raise RepoError(f"{expected_type} signature threshold not met: {len(accepted)}/{role['threshold']}")
    return signed


def verify_root(root: dict[str, Any]) -> tuple[dict[str, ec.EllipticCurvePublicKey], dict[str, Any]]:
    keys, roles = parse_root(root)
    verify_envelope(root, keys, roles["root"], "root")
    return keys, roles


def verify_descriptor(owner: dict[str, Any], name: str, data: bytes, expected_version: int) -> None:
    meta = owner.get("meta")
    if not isinstance(meta, dict) or name not in meta:
        raise RepoError(f"missing descriptor for {name}")
    record = meta[name]
    require_keys(record, {"version", "length", "hashes"}, f"descriptor {name}")
    if record["version"] != expected_version or record["length"] != len(data) or record["hashes"] != {"sha256": sha256(data)}:
        raise RepoError(f"descriptor mismatch for {name}")


def validate_repository(repo: Path, config_path: Path | None = None) -> None:
    metadata = repo / "metadata"
    root = strict_json(metadata / "root.json")
    keys, roles = verify_root(root)
    versioned_roots = sorted(
        (
            (int(match.group(1)), path)
            for path in metadata.glob("*.root.json")
            if (match := re.fullmatch(r"([1-9][0-9]*)\.root\.json", path.name))
        ),
        key=lambda item: item[0],
    )
    if versioned_roots:
        previous: tuple[dict[str, ec.EllipticCurvePublicKey], dict[str, Any], int] | None = None
        for declared_version, path in versioned_roots:
            envelope = strict_json(path)
            current_keys, current_roles = parse_root(envelope)
            actual_version = envelope["signed"]["version"]
            if actual_version != declared_version:
                raise RepoError(f"root filename version mismatch: {path}")
            verify_envelope(envelope, current_keys, current_roles["root"], "root")
            if previous is not None:
                previous_keys, previous_roles, previous_version = previous
                if actual_version != previous_version + 1:
                    raise RepoError("root versions must be consecutive")
                verify_envelope(envelope, previous_keys, previous_roles["root"], "root")
            previous = current_keys, current_roles, actual_version
        newest = strict_json(versioned_roots[-1][1])
        if canonical_bytes(newest) != canonical_bytes(root):
            raise RepoError("metadata/root.json must equal the newest versioned root")
        keys, roles = parse_root(newest)
    timestamp_path = metadata / "timestamp.json"
    timestamp_bytes = timestamp_path.read_bytes()
    timestamp = verify_envelope(strict_json(timestamp_path), keys, roles["timestamp"], "timestamp")
    snapshot_version = require_int(timestamp.get("meta", {}).get("snapshot.json", {}).get("version"), "snapshot descriptor version")
    snapshot_path = metadata / f"{snapshot_version}.snapshot.json"
    snapshot_bytes = snapshot_path.read_bytes()
    verify_descriptor(timestamp, "snapshot.json", snapshot_bytes, snapshot_version)
    snapshot = verify_envelope(strict_json(snapshot_path), keys, roles["snapshot"], "snapshot")
    targets_version = require_int(snapshot.get("meta", {}).get("targets.json", {}).get("version"), "targets descriptor version")
    targets_path = metadata / f"{targets_version}.targets.json"
    targets_bytes = targets_path.read_bytes()
    verify_descriptor(snapshot, "targets.json", targets_bytes, targets_version)
    targets = verify_envelope(strict_json(targets_path), keys, roles["targets"], "targets")
    require_keys(targets, {"_type", "expires", "specVersion", "version", "custom", "targets"}, "targets.signed")
    target_map = targets["targets"]
    if not isinstance(target_map, dict) or len(target_map) not in range(1, 65):
        raise RepoError("targets must contain 1 to 64 APKs")
    source_ids: set[str] = set()
    package_names: set[str] = set()
    for target_name, target in target_map.items():
        if not re.fullmatch(r"apk/[A-Za-z0-9._-]{1,200}\.apk", target_name):
            raise RepoError(f"unsafe APK target path: {target_name}")
        require_keys(target, {"length", "hashes", "custom"}, f"target {target_name}")
        apk = repo / "targets" / target_name
        data = apk.read_bytes()
        if target["length"] != len(data) or target["hashes"] != {"sha256": sha256(data)}:
            raise RepoError(f"APK descriptor mismatch: {target_name}")
        custom = target["custom"]
        required_custom = {"packageName", "name", "versionCode", "versionName", "lang", "apkSignerPins", "lineageRootSha256", "sources"}
        unknown_custom = set(custom) - required_custom - {"acceptedArtifacts"}
        missing_custom = required_custom - set(custom)
        if unknown_custom or missing_custom:
            raise RepoError(f"target custom {target_name} fields differ: missing={sorted(missing_custom)}, unknown={sorted(unknown_custom)}")
        package_name = require_text(custom["packageName"], "packageName")
        if not PACKAGE_RE.fullmatch(package_name) or package_name in package_names:
            raise RepoError(f"invalid or duplicate package: {package_name}")
        package_names.add(package_name)
        current_version = require_int(custom["versionCode"], "versionCode")
        if "acceptedArtifacts" in custom:
            validate_accepted_artifacts(custom["acceptedArtifacts"], current_version, "acceptedArtifacts")
        pins = validate_string_set(custom["apkSignerPins"], "apkSignerPins", minimum=1)
        if any(not SHA256_RE.fullmatch(pin) for pin in pins):
            raise RepoError("invalid APK signer pin")
        require_sha256(custom["lineageRootSha256"], "lineageRootSha256")
        for index, source in enumerate(custom["sources"]):
            expected = set(source) - {"policyHash"}
            if expected != {"id", "name", "lang", "baseUrl", "service", "protocol", "networkPolicy"}:
                raise RepoError(f"Source fields differ in target {target_name}")
            validate_source({key: source[key] for key in expected}, f"{target_name}.sources[{index}]")
            if source["id"] in source_ids:
                raise RepoError(f"duplicate Source id: {source['id']}")
            source_ids.add(source["id"])
            if source["policyHash"] != network_policy_hash(source["networkPolicy"]):
                raise RepoError(f"Source policy hash mismatch: {source['id']}")
        if config_path is None:
            config_path = repo
        verify_apk_contract(apk, custom, config_path)
    print(f"valid repository: {repo}")


def command_validate(args: argparse.Namespace) -> None:
    validate_repository(args.repo.resolve(), args.config.resolve() if args.config else None)


def root_fingerprints(root: dict[str, Any]) -> list[str]:
    _, roles = parse_root(root)
    return sorted(roles["root"]["keyids"])


def command_fingerprints(args: argparse.Namespace) -> None:
    root_path = args.root.resolve()
    if root_path.is_dir():
        root_path = root_path / "metadata/root.json"
    root = strict_json(root_path)
    verify_root(root)
    digest = sha256(canonical_bytes(root))
    print(f"root metadata SHA-256: {digest}")
    print(f"root threshold: {root['signed']['roles']['root']['threshold']}")
    for key_id in root_fingerprints(root):
        print(f"root key: {key_id}")
    print(f"repository domain UUID (UUIDv5 of root digest): {uuid.uuid5(uuid.NAMESPACE_URL, digest)}")


def command_rotate_root(args: argparse.Namespace) -> None:
    config_path = args.config.resolve()
    config = strict_json(config_path)
    output = output_directory(config_path, config)
    old_root_path = output / "metadata/root.json"
    old_root = strict_json(old_root_path)
    old_keys, old_roles = verify_root(old_root)
    old_version = old_root["signed"]["version"]
    new_version = old_version + 1
    new_root_path = output / f"metadata/{new_version}.root.json"
    if new_root_path.exists():
        raise RepoError(f"refusing same-version metadata replacement: {new_root_path}")
    old_private = load_role_keys(config_path, config, "root")
    old_ids = set(old_roles["root"]["keyids"])
    matching_old = [key for key in old_private if public_record(key)[0] in old_ids]
    if len(matching_old) < old_roles["root"]["threshold"]:
        raise RepoError("current config cannot meet the old root threshold; restore old root keys before rotation")
    names = args.new_key_name or [f"root-v{new_version}-{index}" for index in range(1, 4)]
    if len(set(names)) != len(names):
        raise RepoError("new root key names must be unique")
    if any(not SAFE_STEM_RE.fullmatch(name) for name in names):
        raise RepoError("new root key names must use safe file stems")
    threshold = args.threshold or min(2, len(names))
    if threshold not in range(1, len(names) + 1):
        raise RepoError("new root threshold exceeds its key count")
    password = password_for(config["mode"])
    key_dir = (config_path.parent / config["paths"]["keyDirectory"]).resolve()
    ensure_private_directory(key_dir)
    for name in names:
        path = key_dir / f"{name}.pem"
        if path.exists():
            raise RepoError(f"new root key already exists: {path}")
        write_private_key(path, ec.generate_private_key(ec.SECP256R1()), password)
    next_config = copy.deepcopy(config)
    next_config["versions"]["root"] = new_version
    next_config["roles"]["root"] = {"keyNames": names, "threshold": threshold}
    new_root = build_root(config_path, next_config, new_version)
    new_keys, new_roles = parse_root(new_root)
    signed = new_root["signed"]
    combined = sign_envelope(signed, matching_old)
    existing = {item["keyid"] for item in combined["signatures"]}
    for item in new_root["signatures"]:
        if item["keyid"] not in existing:
            combined["signatures"].append(item)
    verify_envelope(combined, old_keys, old_roles["root"], "root")
    verify_envelope(combined, new_keys, new_roles["root"], "root")
    encoded = canonical_bytes(combined)
    write_versioned(new_root_path, encoded)
    write_atomic(old_root_path, encoded)
    write_atomic(config_path, pretty_bytes(next_config))
    print(f"rotated root from version {old_version} to {new_version}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="newshub-extension-repo")
    subparsers = parser.add_subparsers(dest="command", required=True)
    init = subparsers.add_parser("init", help="create a repository config and P-256 keys")
    init.add_argument("--config", type=Path, default=Path("repository-config.json"))
    init.add_argument("--mode", choices=("ephemeral", "production"), default="ephemeral")
    init.add_argument("--key-directory", default=".newshub-repo-keys")
    init.add_argument("--public-directory", default="repository")
    init.set_defaults(handler=command_init)
    publish = subparsers.add_parser("publish", help="build and validate signed metadata and APK targets")
    publish.add_argument("--config", type=Path, default=Path("repository-config.json"))
    publish.set_defaults(handler=command_publish)
    validate = subparsers.add_parser("validate", help="validate a complete repository offline")
    validate.add_argument("repo", type=Path)
    validate.add_argument("--config", type=Path)
    validate.set_defaults(handler=command_validate)
    fingerprints = subparsers.add_parser("fingerprints", help="print root trust fingerprints")
    fingerprints.add_argument("root", type=Path)
    fingerprints.set_defaults(handler=command_fingerprints)
    rotate = subparsers.add_parser("rotate-root", help="publish a consecutively versioned, cross-signed root")
    rotate.add_argument("--config", type=Path, default=Path("repository-config.json"))
    rotate.add_argument("--new-key-name", action="append", help="new root key file stem; repeat for each key")
    rotate.add_argument("--threshold", type=int, help="new root signature threshold")
    rotate.set_defaults(handler=command_rotate_root)
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = build_parser().parse_args(argv)
        args.handler(args)
        return 0
    except (RepoError, OSError, subprocess.TimeoutExpired) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
