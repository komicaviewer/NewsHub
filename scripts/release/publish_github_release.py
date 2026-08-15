#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from urllib import error, parse, request

from release_contract import ReleaseContractError, require_commit_sha, sha256_file


API_ROOT = "https://api.github.com"
UPLOAD_ROOT = "https://uploads.github.com"
REPOSITORY = "komicaviewer/NewsHub"


class GitHubApi:
    def __init__(self, token: str, *, opener=request.urlopen) -> None:
        self._token = token
        self._opener = opener

    def call(
        self,
        method: str,
        url: str,
        *,
        body: bytes | None = None,
        content_type: str = "application/json",
        expected: tuple[int, ...] = (200,),
    ) -> object:
        github_request = request.Request(
            url,
            data=body,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self._token}",
                "Content-Type": content_type,
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "newshub-gcp-release",
            },
        )
        with self._opener(github_request, timeout=60) as response:
            payload = response.read()
            if response.status not in expected:
                raise ReleaseContractError(f"GitHub API returned {response.status}")
        if not payload:
            return None
        return json.loads(payload)


def _json_body(value: dict[str, object]) -> bytes:
    return json.dumps(value, separators=(",", ":")).encode("utf-8")


def _release_for_tag(api: GitHubApi, tag: str) -> dict[str, object] | None:
    try:
        payload = api.call("GET", f"{API_ROOT}/repos/{REPOSITORY}/releases/tags/{parse.quote(tag)}")
    except error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise
    if not isinstance(payload, dict):
        raise ReleaseContractError("GitHub release response is invalid")
    return payload


def _resolve_tag_commit(api: GitHubApi, tag: str) -> str:
    payload = api.call("GET", f"{API_ROOT}/repos/{REPOSITORY}/git/ref/tags/{parse.quote(tag)}")
    if not isinstance(payload, dict) or not isinstance(payload.get("object"), dict):
        raise ReleaseContractError("GitHub tag reference response is invalid")
    target = payload["object"]
    for _ in range(3):
        kind = target.get("type")
        sha = target.get("sha")
        if not isinstance(sha, str):
            break
        if kind == "commit":
            return require_commit_sha(sha)
        if kind != "tag":
            break
        nested = api.call("GET", f"{API_ROOT}/repos/{REPOSITORY}/git/tags/{sha}")
        if not isinstance(nested, dict) or not isinstance(nested.get("object"), dict):
            break
        target = nested["object"]
    raise ReleaseContractError("GitHub tag does not resolve to a commit")


def publish(
    api: GitHubApi,
    *,
    apk: Path,
    verification: dict[str, object],
) -> tuple[str, str]:
    tag = str(verification["tag"])
    commit_sha = require_commit_sha(str(verification["commitSha"]))
    if _resolve_tag_commit(api, tag) != commit_sha:
        raise ReleaseContractError("GitHub tag target changed after Cloud Build checkout")
    release = _release_for_tag(api, tag)
    if release is None:
        created = api.call(
            "POST",
            f"{API_ROOT}/repos/{REPOSITORY}/releases",
            body=_json_body(
                {
                    "tag_name": tag,
                    "target_commitish": commit_sha,
                    "name": tag,
                    "draft": False,
                    "prerelease": False,
                    "generate_release_notes": True,
                }
            ),
            expected=(201,),
        )
        if not isinstance(created, dict):
            raise ReleaseContractError("created GitHub release response is invalid")
        release = created
    release_id = release.get("id")
    html_url = release.get("html_url")
    assets = release.get("assets", [])
    if not isinstance(release_id, int) or not isinstance(html_url, str) or not isinstance(assets, list):
        raise ReleaseContractError("GitHub release metadata is incomplete")
    expected_name = str(verification["apkName"])
    expected_digest = f"sha256:{verification['apkSha256']}"
    expected_size = int(verification["apkBytes"])
    matching = [asset for asset in assets if isinstance(asset, dict) and asset.get("name") == expected_name]
    if matching:
        asset = matching[0]
        if asset.get("digest") != expected_digest or int(asset.get("size", -1)) != expected_size:
            raise ReleaseContractError("existing release asset conflicts with the verified APK")
        return html_url, expected_digest
    uploaded = api.call(
        "POST",
        f"{UPLOAD_ROOT}/repos/{REPOSITORY}/releases/{release_id}/assets?"
        + parse.urlencode({"name": expected_name}),
        body=apk.read_bytes(),
        content_type="application/vnd.android.package-archive",
        expected=(201,),
    )
    if not isinstance(uploaded, dict):
        raise ReleaseContractError("uploaded GitHub release asset response is invalid")
    if int(uploaded.get("size", -1)) != expected_size:
        raise ReleaseContractError("uploaded GitHub asset size does not match the verified APK")
    digest = uploaded.get("digest")
    if digest is not None and digest != expected_digest:
        raise ReleaseContractError("uploaded GitHub asset digest does not match the verified APK")
    return html_url, expected_digest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--verification", type=Path, required=True)
    args = parser.parse_args()
    try:
        token = args.token_file.read_text(encoding="utf-8").strip()
        if not token or "\x00" in token:
            raise ReleaseContractError("GitHub token file is invalid")
        verification = json.loads(args.verification.read_text(encoding="utf-8"))
        if not isinstance(verification, dict):
            raise ReleaseContractError("verification payload is invalid")
        if sha256_file(args.apk) != verification.get("apkSha256"):
            raise ReleaseContractError("APK changed after local verification")
        url, digest = publish(GitHubApi(token), apk=args.apk, verification=verification)
    except (
        OSError,
        ValueError,
        KeyError,
        json.JSONDecodeError,
        error.HTTPError,
        error.URLError,
        ReleaseContractError,
    ) as exc:
        print(f"GitHub release publication failed: {exc}", file=sys.stderr)
        return 1
    print(f"published {url} asset={args.apk.name} digest={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
