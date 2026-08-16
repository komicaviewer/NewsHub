from __future__ import annotations

import base64
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import tempfile
import unittest
import subprocess


RELEASE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_DIR))

from release_contract import ReleaseContractError, parse_release_tag, validate_release_bundle
from reserve_release_cost import CostReservationError, reserve_release
from validate_release_source import validate_git_source, validate_production_root


def bundle(**overrides: object) -> str:
    payload: dict[str, object] = {
        "schemaVersion": 1,
        "bundleType": "newshub-app-release",
        "keyStoreBase64": base64.b64encode(b"test-keystore").decode("ascii"),
        "storePassword": "store-pass",
        "keyAlias": "release",
        "keyPassword": "key-pass",
        "certificateSha256": "ab" * 32,
    }
    payload.update(overrides)
    return json.dumps(payload)


class ReleaseContractTest(unittest.TestCase):
    def test_cloud_build_detached_checkout_verifies_annotated_remote_tag(self) -> None:
        expected = "a" * 40
        tag_object = "b" * 40
        calls: list[list[str]] = []

        def run(command: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
            calls.append(command)
            if command[1:3] == ["rev-parse", "HEAD"]:
                output = expected + "\n"
            elif command[1:4] == ["remote", "get-url", "origin"]:
                output = "https://github.com/komicaviewer/NewsHub.git\n"
            else:
                output = (
                    f"{tag_object}\trefs/tags/v0.0.12\n"
                    f"{expected}\trefs/tags/v0.0.12^{{}}\n"
                )
            return subprocess.CompletedProcess(command, 0, stdout=output, stderr="")

        validate_git_source(Path("/workspace"), "v0.0.12", expected, run=run)
        self.assertIn("ls-remote", calls[-1])
        self.assertNotIn("rev-list", [item for command in calls for item in command])

    def test_remote_tag_must_match_event_commit_and_reviewed_origin(self) -> None:
        expected = "a" * 40

        def runner(origin: str, remote_sha: str):
            def run(command: list[str], **_kwargs: object) -> subprocess.CompletedProcess[str]:
                if command[1:3] == ["rev-parse", "HEAD"]:
                    output = expected + "\n"
                elif command[1:4] == ["remote", "get-url", "origin"]:
                    output = origin + "\n"
                else:
                    output = f"{remote_sha}\trefs/tags/v0.0.12\n"
                return subprocess.CompletedProcess(command, 0, stdout=output, stderr="")
            return run

        with self.assertRaisesRegex(ReleaseContractError, "reviewed NewsHub repository"):
            validate_git_source(
                Path("/workspace"), "v0.0.12", expected,
                run=runner("https://github.com/attacker/NewsHub", expected),
            )
        with self.assertRaisesRegex(ReleaseContractError, "does not resolve"):
            validate_git_source(
                Path("/workspace"), "v0.0.12", expected,
                run=runner("https://github.com/komicaviewer/NewsHub", "c" * 40),
            )

    def test_production_root_must_exist_and_must_not_equal_debug_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            production = root / "marketplace/src/main/assets/extension-root.json"
            debug = root / "marketplace/src/debug/assets/extension-root.json"
            debug.parent.mkdir(parents=True)
            fixture = json.dumps({"signatures": [{"keyid": "test", "sig": "test"}], "signed": {"_type": "root"}})
            debug.write_text(fixture)
            with self.assertRaises(ReleaseContractError):
                validate_production_root(root)
            production.parent.mkdir(parents=True)
            production.write_text(fixture)
            with self.assertRaises(ReleaseContractError):
                validate_production_root(root)

    def test_distinct_production_root_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            production = root / "marketplace/src/main/assets/extension-root.json"
            debug = root / "marketplace/src/debug/assets/extension-root.json"
            production.parent.mkdir(parents=True)
            debug.parent.mkdir(parents=True)
            production.write_text(json.dumps({"signatures": [{"keyid": "production", "sig": "test"}], "signed": {"_type": "root"}}))
            debug.write_text(json.dumps({"signatures": [{"keyid": "debug", "sig": "test"}], "signed": {"_type": "root"}}))
            validate_production_root(root)

    def test_semver_maps_to_android_version(self) -> None:
        version = parse_release_tag("v2.34.56")
        self.assertEqual("2.34.56", version.version_name)
        self.assertEqual(2_034_056, version.version_code)

    def test_noncanonical_or_out_of_range_tag_is_rejected(self) -> None:
        for tag in ("0.0.12", "v0.00.12", "v0.0.0", "v0.1000.1", "v2101.0.0"):
            with self.subTest(tag=tag), self.assertRaises(ReleaseContractError):
                parse_release_tag(tag)

    def test_release_bundle_is_exact_and_decodes_keystore(self) -> None:
        credentials, key_store = validate_release_bundle(bundle())
        self.assertEqual(b"test-keystore", key_store)
        self.assertEqual("ab" * 32, credentials["certificateSha256"])

    def test_release_bundle_rejects_unknown_and_duplicate_keys(self) -> None:
        with self.assertRaises(ReleaseContractError):
            validate_release_bundle(bundle(extra="forbidden"))
        raw = bundle().replace('"schemaVersion": 1', '"schemaVersion": 1, "schemaVersion": 1')
        with self.assertRaises(ReleaseContractError):
            validate_release_bundle(raw)

    def test_cost_reservation_preserves_existing_workflow_state(self) -> None:
        result = reserve_release(
            {
                "month": "2026-08",
                "buildMinutes": 526,
                "repairJobs": 3,
                "hostRepairJobs": 1,
                "days": {"2026-08-14": 3},
            },
            now=datetime(2026, 8, 15, tzinfo=timezone.utc),
            build_minutes=20,
            monthly_build_limit=2_000,
            monthly_release_limit=4,
        )
        self.assertEqual(546, result["buildMinutes"])
        self.assertEqual(1, result["appReleaseJobs"])
        self.assertEqual(3, result["repairJobs"])
        self.assertEqual({"2026-08-14": 3}, result["days"])

    def test_cost_reservation_fails_closed_at_both_limits(self) -> None:
        now = datetime(2026, 8, 15, tzinfo=timezone.utc)
        with self.assertRaises(CostReservationError):
            reserve_release(
                {"month": "2026-08", "buildMinutes": 1_990},
                now=now,
                build_minutes=20,
                monthly_build_limit=2_000,
                monthly_release_limit=4,
            )
        with self.assertRaises(CostReservationError):
            reserve_release(
                {"month": "2026-08", "buildMinutes": 100, "appReleaseJobs": 4},
                now=now,
                build_minutes=20,
                monthly_build_limit=2_000,
                monthly_release_limit=4,
            )

    def test_fourth_release_uses_normal_monthly_limit_without_override(self) -> None:
        result = reserve_release(
            {"month": "2026-08", "buildMinutes": 60, "appReleaseJobs": 3},
            now=datetime(2026, 8, 16, tzinfo=timezone.utc),
            build_minutes=20,
            monthly_build_limit=2_000,
            monthly_release_limit=4,
            release_tag="v9.9.9",
            release_commit_sha="not-needed-for-normal-release",
        )
        self.assertEqual(4, result["appReleaseJobs"])
        self.assertEqual(80, result["buildMinutes"])

    def test_fifth_release_requires_exact_august_v0017_override(self) -> None:
        exact_commit = "d" * 40
        state = {
            "month": "2026-08",
            "buildMinutes": 100,
            "appReleaseJobs": 4,
            "repairJobs": 3,
        }
        result = reserve_release(
            state,
            now=datetime(2026, 8, 16, tzinfo=timezone.utc),
            build_minutes=20,
            monthly_build_limit=2_000,
            monthly_release_limit=4,
            release_tag="v0.0.17",
            release_commit_sha=exact_commit,
            emergency_approved=True,
            emergency_month="2026-08",
            emergency_max_releases=5,
            emergency_tag="v0.0.17",
            emergency_commit_sha=exact_commit,
        )
        self.assertEqual(5, result["appReleaseJobs"])
        self.assertEqual(120, result["buildMinutes"])
        self.assertEqual(4, state["appReleaseJobs"])
        self.assertEqual(100, state["buildMinutes"])

    def test_sixth_release_requires_exact_august_v0019_override(self) -> None:
        exact_commit = "e" * 40
        state = {
            "month": "2026-08",
            "buildMinutes": 120,
            "appReleaseJobs": 5,
            "repairJobs": 3,
        }
        result = reserve_release(
            state,
            now=datetime(2026, 8, 16, tzinfo=timezone.utc),
            build_minutes=20,
            monthly_build_limit=2_000,
            monthly_release_limit=4,
            release_tag="v0.0.19",
            release_commit_sha=exact_commit,
            emergency_approved=True,
            emergency_month="2026-08",
            emergency_max_releases=6,
            emergency_tag="v0.0.19",
            emergency_commit_sha=exact_commit,
        )
        self.assertEqual(6, result["appReleaseJobs"])
        self.assertEqual(140, result["buildMinutes"])
        self.assertEqual(5, state["appReleaseJobs"])
        self.assertEqual(120, state["buildMinutes"])

    def test_seventh_release_requires_exact_august_v0020_override(self) -> None:
        exact_commit = "a" * 40
        state = {
            "month": "2026-08",
            "buildMinutes": 140,
            "appReleaseJobs": 6,
            "repairJobs": 3,
        }
        result = reserve_release(
            state,
            now=datetime(2026, 8, 17, tzinfo=timezone.utc),
            build_minutes=20,
            monthly_build_limit=2_000,
            monthly_release_limit=4,
            release_tag="v0.0.20",
            release_commit_sha=exact_commit,
            emergency_approved=True,
            emergency_month="2026-08",
            emergency_max_releases=7,
            emergency_tag="v0.0.20",
            emergency_commit_sha=exact_commit,
        )
        self.assertEqual(7, result["appReleaseJobs"])
        self.assertEqual(160, result["buildMinutes"])
        self.assertEqual(6, state["appReleaseJobs"])
        self.assertEqual(140, state["buildMinutes"])

    def test_emergency_override_rejects_mixed_tuple_other_month_tag_and_commit(self) -> None:
        exact_commit = "d" * 40
        base = {
            "build_minutes": 20,
            "monthly_build_limit": 2_000,
            "monthly_release_limit": 4,
            "release_tag": "v0.0.17",
            "release_commit_sha": exact_commit,
            "emergency_approved": True,
            "emergency_month": "2026-08",
            "emergency_max_releases": 5,
            "emergency_tag": "v0.0.17",
            "emergency_commit_sha": exact_commit,
        }
        cases = (
            (
                {"month": "2026-09", "buildMinutes": 100, "appReleaseJobs": 4},
                datetime(2026, 9, 1, tzinfo=timezone.utc),
                {},
            ),
            (
                {"month": "2026-08", "buildMinutes": 100, "appReleaseJobs": 4},
                datetime(2026, 8, 16, tzinfo=timezone.utc),
                {"release_tag": "v0.0.18"},
            ),
            (
                {"month": "2026-08", "buildMinutes": 100, "appReleaseJobs": 4},
                datetime(2026, 8, 16, tzinfo=timezone.utc),
                {"release_commit_sha": "e" * 40},
            ),
            (
                {"month": "2026-08", "buildMinutes": 120, "appReleaseJobs": 5},
                datetime(2026, 8, 16, tzinfo=timezone.utc),
                {
                    "release_tag": "v0.0.19",
                    "emergency_max_releases": 6,
                    "emergency_tag": "v0.0.17",
                },
            ),
            (
                {"month": "2026-08", "buildMinutes": 100, "appReleaseJobs": 4},
                datetime(2026, 8, 16, tzinfo=timezone.utc),
                {
                    "release_tag": "v0.0.19",
                    "emergency_max_releases": 6,
                    "emergency_tag": "v0.0.19",
                },
            ),
            (
                {"month": "2026-08", "buildMinutes": 120, "appReleaseJobs": 5},
                datetime(2026, 8, 16, tzinfo=timezone.utc),
                {},
            ),
        )
        for state, now, overrides in cases:
            original = dict(state)
            with self.subTest(state=state, overrides=overrides), self.assertRaises(CostReservationError):
                reserve_release(state, now=now, **(base | overrides))
            self.assertEqual(original, state)

    def test_emergency_override_rejects_eighth_and_malformed_shas_without_mutation(self) -> None:
        exact_commit = "f" * 40
        base = {
            "build_minutes": 20,
            "monthly_build_limit": 2_000,
            "monthly_release_limit": 4,
            "release_tag": "v0.0.19",
            "release_commit_sha": exact_commit,
            "emergency_approved": True,
            "emergency_month": "2026-08",
            "emergency_max_releases": 6,
            "emergency_tag": "v0.0.19",
            "emergency_commit_sha": exact_commit,
        }
        cases = (
            (
                {"month": "2026-08", "buildMinutes": 160, "appReleaseJobs": 7},
                {
                    "release_tag": "v0.0.20",
                    "emergency_max_releases": 7,
                    "emergency_tag": "v0.0.20",
                },
            ),
            (
                {"month": "2026-08", "buildMinutes": 120, "appReleaseJobs": 5},
                {"release_commit_sha": "F" * 40},
            ),
            (
                {"month": "2026-08", "buildMinutes": 120, "appReleaseJobs": 5},
                {"emergency_commit_sha": "F" * 40},
            ),
            (
                {"month": "2026-08", "buildMinutes": 120, "appReleaseJobs": 5},
                {"release_commit_sha": "F" * 40, "emergency_commit_sha": "F" * 40},
            ),
            (
                {"month": "2026-08", "buildMinutes": 120, "appReleaseJobs": 5},
                {"release_commit_sha": "f" * 39, "emergency_commit_sha": "f" * 39},
            ),
        )
        for state, overrides in cases:
            original = dict(state)
            with self.subTest(state=state, overrides=overrides), self.assertRaises(CostReservationError):
                reserve_release(
                    state,
                    now=datetime(2026, 8, 16, tzinfo=timezone.utc),
                    **(base | overrides),
                )
            self.assertEqual(original, state)

    def test_monthly_release_limit_cannot_be_raised_to_five(self) -> None:
        with self.assertRaises(CostReservationError):
            reserve_release(
                {"month": "2026-08", "buildMinutes": 0, "appReleaseJobs": 0},
                now=datetime(2026, 8, 16, tzinfo=timezone.utc),
                build_minutes=20,
                monthly_build_limit=2_000,
                monthly_release_limit=5,
            )
    def test_cloud_build_contract_is_bounded_and_version_pinned(self) -> None:
        workflow = (RELEASE_DIR.parents[1] / "cloudbuild" / "release.yaml").read_text()
        self.assertIn("timeout: 1200s", workflow)
        self.assertIn("queueTtl: 300s", workflow)
        self.assertIn("machineType: E2_STANDARD_2", workflow)
        self.assertIn("--if-generation-match", workflow)
        self.assertLess(
            workflow.index("validate-release-source-before-cost-reservation"),
            workflow.index("reserve-bounded-release-cost"),
        )
        self.assertIn("certificate-and-digest", workflow)
        self.assertIn("_NEWSHUB_APP_SIGNING_CERT_SHA256", workflow)
        self.assertIn("_APP_RELEASE_EMERGENCY_OVERRIDE_APPROVED", workflow)
        self.assertIn("--release-commit-sha", workflow)
        self.assertIn("--emergency-commit-sha", workflow)
        self.assertNotIn("/versions/latest", workflow)
        self.assertNotIn("retry", workflow.lower())


if __name__ == "__main__":
    unittest.main()
