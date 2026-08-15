#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib import error, request

from release_contract import ReleaseContractError, load_strict_json, write_private


PEM_PATTERN = re.compile(
    r"-----BEGIN (?:RSA )?PRIVATE KEY-----\n[\s\S]+\n-----END (?:RSA )?PRIVATE KEY-----\n?"
)


def _b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _request_json(url: str, *, bearer: str, body: dict[str, object]) -> dict[str, object]:
    encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
    github_request = request.Request(
        url,
        data=encoded,
        method="POST",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {bearer}",
            "Content-Type": "application/json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "newshub-gcp-release",
        },
    )
    with request.urlopen(github_request, timeout=30) as response:
        if response.status != 201:
            raise ReleaseContractError(f"GitHub token endpoint returned {response.status}")
        payload = json.loads(response.read())
    if not isinstance(payload, dict):
        raise ReleaseContractError("GitHub token response is invalid")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-id", required=True)
    parser.add_argument("--installation-id", required=True)
    parser.add_argument("--private-dir", type=Path, required=True)
    parser.add_argument("--token-output", type=Path, required=True)
    args = parser.parse_args()
    key_path = args.private_dir / "github-app.pem"
    try:
        if not args.app_id.isdecimal() or int(args.app_id) <= 0:
            raise ReleaseContractError("GitHub App id is invalid")
        if not args.installation_id.isdecimal() or int(args.installation_id) <= 0:
            raise ReleaseContractError("GitHub App installation id is invalid")
        payload = load_strict_json(sys.stdin.read())
        if set(payload) != {"schemaVersion", "bundleType", "privateKeyPem"}:
            raise ReleaseContractError("OPS GitHub bundle keys do not match the strict allowlist")
        if payload.get("schemaVersion") != 1 or payload.get("bundleType") != "ops-github":
            raise ReleaseContractError("OPS GitHub bundle identity is invalid")
        private_key = payload.get("privateKeyPem")
        if not isinstance(private_key, str) or PEM_PATTERN.fullmatch(private_key) is None:
            raise ReleaseContractError("OPS GitHub private key is invalid")
        args.private_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        write_private(key_path, private_key.encode("utf-8"))
        now = int(time.time())
        header = _b64url(b'{"alg":"RS256","typ":"JWT"}')
        claims = _b64url(
            json.dumps(
                {"iat": now - 30, "exp": now + 480, "iss": args.app_id},
                separators=(",", ":"),
            ).encode("utf-8")
        )
        unsigned = f"{header}.{claims}".encode("ascii")
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(key_path)],
            input=unsigned,
            check=True,
            capture_output=True,
        ).stdout
        jwt = f"{header}.{claims}.{_b64url(signature)}"
        response = _request_json(
            f"https://api.github.com/app/installations/{args.installation_id}/access_tokens",
            bearer=jwt,
            body={"repositories": ["NewsHub"], "permissions": {"contents": "write"}},
        )
        token = response.get("token")
        expires_at = response.get("expires_at")
        if not isinstance(token, str) or not token or not isinstance(expires_at, str):
            raise ReleaseContractError("GitHub installation token response is incomplete")
        expiry = datetime.fromisoformat(expires_at.replace("Z", "+00:00"))
        if expiry <= datetime.now(timezone.utc):
            raise ReleaseContractError("GitHub installation token is already expired")
        write_private(args.token_output, token.encode("utf-8"))
    except (
        OSError,
        ValueError,
        json.JSONDecodeError,
        subprocess.CalledProcessError,
        error.HTTPError,
        error.URLError,
        ReleaseContractError,
    ) as exc:
        print(f"GitHub App token preparation failed: {exc}", file=sys.stderr)
        return 1
    finally:
        key_path.unlink(missing_ok=True)
    print("wrote a repository-restricted GitHub App installation token")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
