from __future__ import annotations

import base64
import binascii
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
from typing import Any


TAG_PATTERN = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
CERT_PATTERN = re.compile(r"^[0-9a-f]{64}$")
RELEASE_BUNDLE_KEYS = {
    "schemaVersion",
    "bundleType",
    "keyStoreBase64",
    "storePassword",
    "keyAlias",
    "keyPassword",
    "certificateSha256",
}


class ReleaseContractError(RuntimeError):
    pass


@dataclass(frozen=True)
class ReleaseVersion:
    tag: str
    version_name: str
    version_code: int


def parse_release_tag(tag: str) -> ReleaseVersion:
    match = TAG_PATTERN.fullmatch(tag)
    if match is None:
        raise ReleaseContractError("tag must use canonical vMAJOR.MINOR.PATCH")
    major, minor, patch = (int(value) for value in match.groups())
    if minor >= 1_000 or patch >= 1_000:
        raise ReleaseContractError("minor and patch versions must be below 1000")
    version_code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= version_code <= 2_100_000_000:
        raise ReleaseContractError("calculated versionCode is outside the Android range")
    return ReleaseVersion(tag=tag, version_name=tag[1:], version_code=version_code)


def require_commit_sha(value: str) -> str:
    normalized = value.strip().lower()
    if SHA_PATTERN.fullmatch(normalized) is None:
        raise ReleaseContractError("commit SHA must be an exact 40-character lowercase SHA")
    return normalized


def normalize_certificate_sha256(value: str) -> str:
    normalized = value.replace(":", "").strip().lower()
    if CERT_PATTERN.fullmatch(normalized) is None:
        raise ReleaseContractError("certificate SHA-256 is invalid")
    return normalized


def _reject_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ReleaseContractError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_strict_json(raw: str, *, maximum_bytes: int = 65_535) -> dict[str, Any]:
    if len(raw.encode("utf-8")) > maximum_bytes:
        raise ReleaseContractError("JSON payload exceeds the release limit")
    try:
        payload = json.loads(raw, object_pairs_hook=_reject_duplicates)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise ReleaseContractError("JSON payload is invalid") from exc
    if not isinstance(payload, dict):
        raise ReleaseContractError("JSON payload must be an object")
    return payload


def _secret_string(payload: dict[str, Any], key: str, *, maximum_bytes: int = 4_096) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value or "\x00" in value:
        raise ReleaseContractError(f"{key} is missing or invalid")
    if len(value.encode("utf-8")) > maximum_bytes:
        raise ReleaseContractError(f"{key} exceeds its size limit")
    return value


def validate_release_bundle(raw: str) -> tuple[dict[str, str], bytes]:
    payload = load_strict_json(raw)
    if set(payload) != RELEASE_BUNDLE_KEYS:
        raise ReleaseContractError("release bundle keys do not match the strict allowlist")
    if payload.get("schemaVersion") != 1 or payload.get("bundleType") != "newshub-app-release":
        raise ReleaseContractError("release bundle identity is invalid")
    encoded = _secret_string(payload, "keyStoreBase64", maximum_bytes=60_000)
    try:
        key_store = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise ReleaseContractError("keyStoreBase64 is not strict base64") from exc
    if not 1 <= len(key_store) <= 48_000:
        raise ReleaseContractError("decoded keystore size is invalid")
    credentials = {
        "storePassword": _secret_string(payload, "storePassword"),
        "keyAlias": _secret_string(payload, "keyAlias", maximum_bytes=512),
        "keyPassword": _secret_string(payload, "keyPassword"),
        "certificateSha256": normalize_certificate_sha256(
            _secret_string(payload, "certificateSha256", maximum_bytes=128)
        ),
    }
    return credentials, key_store


def write_private(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(data)
    except BaseException:
        path.unlink(missing_ok=True)
        raise


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
