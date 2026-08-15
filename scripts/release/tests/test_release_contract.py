from __future__ import annotations

import base64
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import tempfile
import unittest


RELEASE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_DIR))

from release_contract import ReleaseContractError, parse_release_tag, validate_release_bundle
from reserve_release_cost import CostReservationError, reserve_release
from validate_release_source import validate_production_root


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
        self.assertNotIn("/versions/latest", workflow)
        self.assertNotIn("retry", workflow.lower())


if __name__ == "__main__":
    unittest.main()
