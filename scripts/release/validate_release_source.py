#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from typing import Callable

from release_contract import ReleaseContractError, load_strict_json, parse_release_tag, require_commit_sha


def validate_production_root(source_root: Path) -> None:
    production = source_root / "marketplace/src/main/assets/extension-root.json"
    debug = source_root / "marketplace/src/debug/assets/extension-root.json"
    if not production.is_file() or production.stat().st_size == 0:
        raise ReleaseContractError("reviewed production TUF root is not provisioned")
    if production.stat().st_size > 256 * 1024:
        raise ReleaseContractError("production TUF root exceeds its size limit")
    raw = production.read_text(encoding="utf-8")
    root = load_strict_json(raw, maximum_bytes=256 * 1024)
    signed = root.get("signed")
    signatures = root.get("signatures")
    if set(root) != {"signed", "signatures"} or not isinstance(signed, dict):
        raise ReleaseContractError("production TUF root envelope is invalid")
    if signed.get("_type") != "root" or not isinstance(signatures, list) or not signatures:
        raise ReleaseContractError("production TUF root identity is invalid")
    if debug.is_file() and production.read_bytes() == debug.read_bytes():
        raise ReleaseContractError("debug fixture TUF root cannot be promoted to production")


Run = Callable[..., subprocess.CompletedProcess[str]]


def validate_git_source(
    source_root: Path,
    tag: str,
    commit_sha: str,
    *,
    run: Run = subprocess.run,
) -> None:
    parse_release_tag(tag)
    expected_sha = require_commit_sha(commit_sha)
    actual_sha = run(
        ["git", "rev-parse", "HEAD"],
        cwd=source_root,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip().lower()
    if actual_sha != expected_sha:
        raise ReleaseContractError("Cloud Build source does not match the triggering commit")

    origin = run(
        ["git", "remote", "get-url", "origin"],
        cwd=source_root,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip()
    normalized_origin = origin.removesuffix(".git").removesuffix("/")
    if normalized_origin not in {
        "https://github.com/komicaviewer/NewsHub",
        "git@github.com:komicaviewer/NewsHub",
    }:
        raise ReleaseContractError("release source origin is not the reviewed NewsHub repository")

    remote = run(
        [
            "git", "ls-remote", "--exit-code", "--tags", "origin",
            f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}",
        ],
        cwd=source_root,
        check=True,
        text=True,
        capture_output=True,
    )
    refs: dict[str, str] = {}
    allowed_refs = {f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}"}
    for line in remote.stdout.splitlines():
        sha, separator, ref = line.partition("\t")
        if not separator or ref not in allowed_refs or ref in refs:
            raise ReleaseContractError("release tag lookup returned unexpected refs")
        refs[ref] = require_commit_sha(sha)
    direct = refs.get(f"refs/tags/{tag}")
    tag_sha = refs.get(f"refs/tags/{tag}^{{}}", direct)
    if direct is None or tag_sha is None:
        raise ReleaseContractError("release tag was not found on the reviewed origin")
    if tag_sha != expected_sha:
        raise ReleaseContractError("release tag does not resolve to the triggering commit")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    args = parser.parse_args()
    try:
        validate_git_source(args.source_root, args.tag, args.commit_sha)
        validate_production_root(args.source_root)
    except (OSError, UnicodeDecodeError, subprocess.CalledProcessError, ReleaseContractError) as exc:
        print(f"release source validation failed: {exc}", file=sys.stderr)
        return 1
    print(f"release source accepted: {args.tag} commit={args.commit_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
