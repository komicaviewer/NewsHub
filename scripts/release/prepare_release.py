#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys

from release_contract import (
    ReleaseContractError,
    normalize_certificate_sha256,
    parse_release_tag,
    require_commit_sha,
    validate_release_bundle,
    write_private,
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--private-dir", type=Path, required=True)
    parser.add_argument("--metadata-output", type=Path, required=True)
    parser.add_argument("--expected-certificate-sha256", required=True)
    args = parser.parse_args()
    try:
        version = parse_release_tag(args.tag)
        expected_sha = require_commit_sha(args.commit_sha)
        actual_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=args.source_root,
            check=True,
            text=True,
            capture_output=True,
        ).stdout.strip().lower()
        if actual_sha != expected_sha:
            raise ReleaseContractError("Cloud Build source does not match the triggering commit")
        credentials, key_store = validate_release_bundle(sys.stdin.read())
        pinned_certificate = normalize_certificate_sha256(args.expected_certificate_sha256)
        if credentials["certificateSha256"] != pinned_certificate:
            raise ReleaseContractError("release bundle certificate does not match the IaC pin")
        args.private_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
        write_private(args.private_dir / "keystore.jks", key_store)
        for field in ("storePassword", "keyAlias", "keyPassword"):
            write_private(args.private_dir / field, credentials[field].encode("utf-8"))
        metadata = {
            "schemaVersion": 1,
            "tag": version.tag,
            "versionName": version.version_name,
            "versionCode": version.version_code,
            "commitSha": expected_sha,
            "certificateSha256": pinned_certificate,
        }
        args.metadata_output.parent.mkdir(parents=True, exist_ok=True)
        args.metadata_output.write_text(json.dumps(metadata, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, subprocess.CalledProcessError, ReleaseContractError) as exc:
        print(f"release preparation failed: {exc}", file=sys.stderr)
        return 1
    print(
        f"prepared {version.tag} versionName={version.version_name} "
        f"versionCode={version.version_code} commit={expected_sha}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
