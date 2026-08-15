#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

from release_contract import ReleaseContractError, normalize_certificate_sha256, sha256_file


VERSION_CODE_PATTERN = re.compile(r"versionCode='([0-9]+)'")
VERSION_NAME_PATTERN = re.compile(r"versionName='([^']+)'")
CERT_PATTERN = re.compile(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F:]+)")


def _run(command: list[str]) -> str:
    return subprocess.run(command, check=True, text=True, capture_output=True).stdout


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--aapt", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
        if not args.apk.is_file() or args.apk.stat().st_size <= 0:
            raise ReleaseContractError("release APK is missing or empty")
        badging = _run([str(args.aapt), "dump", "badging", str(args.apk)])
        code_match = VERSION_CODE_PATTERN.search(badging)
        name_match = VERSION_NAME_PATTERN.search(badging)
        if code_match is None or name_match is None:
            raise ReleaseContractError("aapt did not report package version metadata")
        if int(code_match.group(1)) != int(metadata["versionCode"]):
            raise ReleaseContractError("APK versionCode does not match the tag")
        if name_match.group(1) != metadata["versionName"]:
            raise ReleaseContractError("APK versionName does not match the tag")
        signer = _run([str(args.apksigner), "verify", "--print-certs", str(args.apk)])
        cert_match = CERT_PATTERN.search(signer)
        if cert_match is None:
            raise ReleaseContractError("apksigner did not report a signer certificate")
        actual_cert = normalize_certificate_sha256(cert_match.group(1))
        if actual_cert != metadata["certificateSha256"]:
            raise ReleaseContractError("APK signer certificate does not match the approved lineage")
        result = {
            **metadata,
            "apkName": args.apk.name,
            "apkBytes": args.apk.stat().st_size,
            "apkSha256": sha256_file(args.apk),
        }
        args.output.write_text(json.dumps(result, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError, ReleaseContractError) as exc:
        print(f"release APK verification failed: {exc}", file=sys.stderr)
        return 1
    print(
        f"verified {result['apkName']} sha256={result['apkSha256']} "
        f"certificate={result['certificateSha256']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
