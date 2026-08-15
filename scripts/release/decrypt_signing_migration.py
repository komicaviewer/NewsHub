#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from release_contract import (
    RELEASE_BUNDLE_KEYS,
    ReleaseContractError,
    load_strict_json,
    normalize_certificate_sha256,
    validate_release_bundle,
)


class MigrationError(RuntimeError):
    pass


def run_quiet(command: list[str], *, env: dict[str, str] | None = None) -> None:
    try:
        subprocess.run(
            command,
            check=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            env=env,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        raise MigrationError("cryptographic validation command failed") from exc


def decrypt_and_validate(
    encrypted: Path,
    recipient_certificate: Path,
    recipient_private_key: Path,
    expected_certificate_sha256: str,
    output: Path,
) -> None:
    if not encrypted.is_file() or encrypted.stat().st_size == 0:
        raise MigrationError("encrypted migration artifact is missing or empty")
    if output.exists():
        raise MigrationError("refusing to overwrite migration output")
    expected = normalize_certificate_sha256(expected_certificate_sha256)
    with tempfile.TemporaryDirectory(prefix="newshub-signing-migration-") as temporary:
        work = Path(temporary)
        work.chmod(0o700)
        decrypted = work / "bundle.json"
        run_quiet(
            [
                "openssl", "cms", "-decrypt", "-binary", "-inform", "DER",
                "-in", str(encrypted), "-recip", str(recipient_certificate),
                "-inkey", str(recipient_private_key), "-out", str(decrypted),
            ]
        )
        decrypted.chmod(0o600)
        raw = decrypted.read_text(encoding="utf-8")
        payload = load_strict_json(raw)
        if set(payload) != RELEASE_BUNDLE_KEYS or not isinstance(payload.get("keyStoreBase64"), str):
            raise MigrationError("migration bundle schema is invalid")
        # GNU base64 used by the historical workflow accepted wrapped input. Canonicalize
        # ASCII whitespace exactly once during migration; runtime bundles remain strict.
        payload["keyStoreBase64"] = "".join(payload["keyStoreBase64"].split())
        raw = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        credentials, key_store = validate_release_bundle(raw)
        if credentials["certificateSha256"] != expected:
            raise MigrationError("migration certificate does not match the production APK pin")

        key_store_path = work / "newshub-release.keystore"
        certificate_path = work / "certificate.der"
        key_store_path.write_bytes(key_store)
        key_store_path.chmod(0o600)
        key_env = {
            "PATH": os.environ.get("PATH", ""),
            "NEWSHUB_STORE_PASSWORD": credentials["storePassword"],
            "NEWSHUB_KEY_PASSWORD": credentials["keyPassword"],
        }
        run_quiet(
            [
                "keytool", "-importkeystore", "-noprompt",
                "-srckeystore", str(key_store_path),
                "-srcstorepass:env", "NEWSHUB_STORE_PASSWORD",
                "-srcalias", credentials["keyAlias"],
                "-srckeypass:env", "NEWSHUB_KEY_PASSWORD",
                "-destkeystore", str(work / "key-validation.p12"),
                "-deststoretype", "PKCS12",
                "-deststorepass:env", "NEWSHUB_STORE_PASSWORD",
            ],
            env=key_env,
        )
        run_quiet(
            [
                "keytool", "-exportcert", "-keystore", str(key_store_path),
                "-storepass:env", "NEWSHUB_STORE_PASSWORD", "-alias", credentials["keyAlias"],
                "-file", str(certificate_path),
            ],
            env=key_env,
        )
        fingerprint = subprocess.run(
            [
                "openssl", "x509", "-inform", "DER", "-in", str(certificate_path),
                "-noout", "-fingerprint", "-sha256",
            ],
            check=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        ).stdout.partition("=")[2].strip()
        if normalize_certificate_sha256(fingerprint) != expected:
            raise MigrationError("keystore certificate does not match the production APK pin")

        output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as destination:
            destination.write(raw)
            destination.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--encrypted", type=Path, required=True)
    parser.add_argument("--recipient-cert", type=Path, required=True)
    parser.add_argument("--recipient-key", type=Path, required=True)
    parser.add_argument("--expected-certificate-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        decrypt_and_validate(
            args.encrypted,
            args.recipient_cert,
            args.recipient_key,
            args.expected_certificate_sha256,
            args.output,
        )
    except (MigrationError, ReleaseContractError, OSError, UnicodeDecodeError, subprocess.CalledProcessError) as exc:
        print(f"signing migration failed: {exc}", file=sys.stderr)
        return 1
    print("signing migration decrypted and validated without exposing secret values")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
