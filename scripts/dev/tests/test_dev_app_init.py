import unittest
from pathlib import Path

SCRIPT = (Path(__file__).resolve().parents[1] / "dev-app.init.gradle").read_text()


class DevAppInitScriptTest(unittest.TestCase):
    def test_uses_dev_trust_base_url(self):
        self.assertIn(
            "https://raw.githubusercontent.com/komicaviewer/extensions/dev", SCRIPT
        )

    def test_uses_debug_extension_package_suffix(self):
        self.assertIn("newshubExtensionPackageSuffix", SCRIPT)
        self.assertIn("'.debug'", SCRIPT)

    def test_keeps_debug_application_id_suffix(self):
        self.assertIn("applicationIdSuffix = '.debug'", SCRIPT)

    def test_marketplace_debug_assets_point_to_dev_root(self):
        self.assertIn("src/dev/assets", SCRIPT)
        self.assertIn(
            "Dev App build requires marketplace/src/dev/assets/extension-root.json",
            SCRIPT,
        )

    def test_does_not_fall_back_to_prod_root(self):
        self.assertNotIn("setSrcDirs([])", SCRIPT)
        self.assertNotIn("src/main/assets/extension-root.json", SCRIPT)


if __name__ == "__main__":
    unittest.main()
