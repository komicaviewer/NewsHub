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


    def test_app_debug_resources_include_dev_badge(self):
        self.assertIn("src/dev/res", SCRIPT)

    def test_dev_resources_rename_app_and_badge_icon(self):
        res = Path(__file__).resolve().parents[3] / "app" / "src" / "dev" / "res"
        strings = (res / "values" / "strings.xml").read_text()
        self.assertIn('<string name="app_name">NewsHub Dev</string>', strings)
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            icon = (res / "mipmap-anydpi-v26" / name).read_text()
            self.assertIn("@drawable/ic_launcher_foreground_dev", icon)
            self.assertIn("@drawable/ic_launcher_monochrome_dev", icon)


if __name__ == "__main__":
    unittest.main()
