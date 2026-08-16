from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest
from urllib import error


RELEASE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_DIR))

from publish_github_release import GitHubApi, publish
from release_contract import ReleaseContractError, sha256_file


class Response:
    def __init__(self, status: int, payload: object) -> None:
        self.status = status
        self._body = json.dumps(payload).encode("utf-8")

    def read(self) -> bytes:
        return self._body

    def __enter__(self) -> "Response":
        return self

    def __exit__(self, *_args: object) -> None:
        return None


class PublishReleaseTest(unittest.TestCase):
    def _fixture(self, root: Path) -> tuple[Path, dict[str, object]]:
        apk = root / "newshub-v0.0.12.apk"
        apk.write_bytes(b"signed-apk")
        verification: dict[str, object] = {
            "tag": "v0.0.12",
            "commitSha": "a" * 40,
            "apkName": apk.name,
            "apkBytes": apk.stat().st_size,
            "apkSha256": sha256_file(apk),
        }
        return apk, verification

    def test_existing_exact_asset_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk, verification = self._fixture(Path(directory))
            digest = f"sha256:{verification['apkSha256']}"

            def opener(github_request: object, **_kwargs: object) -> Response:
                url = github_request.full_url
                if "/git/ref/tags/" in url:
                    return Response(200, {"object": {"type": "tag", "sha": "b" * 40}})
                if "/git/tags/" in url:
                    return Response(200, {"object": {"type": "commit", "sha": "a" * 40}})
                if "/releases/tags/" in url:
                    return Response(
                        200,
                        {
                            "id": 12,
                            "html_url": "https://github.com/komicaviewer/NewsHub/releases/tag/v0.0.12",
                            "assets": [
                                {
                                    "name": apk.name,
                                    "size": apk.stat().st_size,
                                    "digest": digest,
                                }
                            ],
                        },
                    )
                raise AssertionError(url)

            url, actual_digest = publish(
                GitHubApi("token", opener=opener), apk=apk, verification=verification
            )
            self.assertTrue(url.endswith("/v0.0.12"))
            self.assertEqual(digest, actual_digest)

    def test_conflicting_existing_asset_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk, verification = self._fixture(Path(directory))

            def opener(github_request: object, **_kwargs: object) -> Response:
                url = github_request.full_url
                if "/git/ref/tags/" in url:
                    return Response(200, {"object": {"type": "commit", "sha": "a" * 40}})
                if "/releases/tags/" in url:
                    return Response(
                        200,
                        {
                            "id": 12,
                            "html_url": "https://example.invalid/release",
                            "assets": [
                                {"name": apk.name, "size": apk.stat().st_size, "digest": "sha256:" + "0" * 64}
                            ],
                        },
                    )
                raise AssertionError(url)

            with self.assertRaises(ReleaseContractError):
                publish(GitHubApi("token", opener=opener), apk=apk, verification=verification)


if __name__ == "__main__":
    unittest.main()
