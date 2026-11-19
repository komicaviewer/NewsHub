import subprocess
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]


class AppReleaseSuffixGuardTest(unittest.TestCase):
    def run_task(self, *props):
        return subprocess.run(
            [str(REPO / "gradlew"), "-q", "--offline", ":app:verifyAppReleaseEnvironment", *props],
            capture_output=True, text=True, cwd=REPO,
        )

    def test_blocks_dev_suffix(self):
        result = self.run_task("-PnewshubExtensionPackageSuffix=.debug")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Release blocked", result.stderr)

    def test_allows_prod_default(self):
        result = self.run_task()
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
