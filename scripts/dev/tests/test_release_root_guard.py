import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
GUARD = REPO / "gradle" / "release-root-guard.gradle"
GRADLEW = REPO / "gradlew"


def root_doc(keyids):
    return json.dumps({"signed": {"roles": {"root": {"keyids": keyids}}}, "signatures": []})


class ReleaseRootGuardTest(unittest.TestCase):
    def run_guard(self, prod_text, dev_text):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            shutil.copy(GUARD, tmp / "guard.gradle")
            (tmp / "settings.gradle").write_text("rootProject.name = 'guard-probe'\n")
            (tmp / "prod.json").write_text(prod_text)
            if dev_text is not None:
                (tmp / "dev.json").write_text(dev_text)
            (tmp / "build.gradle").write_text(
                "apply from: 'guard.gradle'\n"
                "tasks.register('probe') { doLast {\n"
                "  checkReleaseRootIsNotDev(file('prod.json'), file('dev.json'))\n"
                "} }\n"
            )
            return subprocess.run(
                [str(GRADLEW), "-p", str(tmp), "probe", "-q", "--offline"],
                capture_output=True, text=True, cwd=REPO,
            )

    def test_blocks_identical_root(self):
        text = root_doc(["a", "b"])
        result = self.run_guard(text, text)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("dev root", result.stderr)

    def test_blocks_shared_root_keyid(self):
        result = self.run_guard(root_doc(["a", "x"]), root_doc(["a", "b"]))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("dev root", result.stderr)

    def test_allows_distinct_roots(self):
        result = self.run_guard(root_doc(["x", "y"]), root_doc(["a", "b"]))
        self.assertEqual(0, result.returncode, result.stderr)

    def test_allows_when_dev_root_absent(self):
        result = self.run_guard(root_doc(["x", "y"]), None)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_real_prod_root_is_not_dev_root(self):
        result = subprocess.run(
            [str(GRADLEW), "-q", "--offline", ":marketplace:verifyReleaseRootIsNotDev"],
            capture_output=True, text=True, cwd=REPO,
        )
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
