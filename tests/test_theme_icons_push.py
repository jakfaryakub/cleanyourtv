"""tools/theme.sh and theme.ps1 push a theme's icons/ (app logos) and empty it again, and the launcher takes the same names.

A theme can carry one logo per app as icons/<package>.png (config.json "app_logos"); the files live in the theme
folder and are never bundled in the APK. These tests run the real tools/theme.sh against the stub TV of
tests/test_theme_push_ownership.py: a push sends every logo, empties the folder on the TV first so a logo from the
theme before does not linger, refuses a name that is not a package before anything is sent, and --reset empties
the folder too. The last checks read ThemeFiles.java and theme.ps1, so the launcher's provider and the Windows twin
take the same names the shell script sends.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_push_ownership as harness  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
THEME_FILES = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/ThemeFiles.java"
THEME_PS1 = ROOT / "tools/theme.ps1"
THEME_SH = ROOT / "tools/theme.sh"

FAKE_PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 40


class ThemeIconPushTest(unittest.TestCase):
    setUp = harness.ThemePushTest.setUp
    run_theme = harness.ThemePushTest.run_theme
    provider_calls = harness.ThemePushTest.provider_calls

    def icons_on_tv(self):
        folder = self.device / "icons"
        return sorted(p.name for p in folder.iterdir()) if folder.is_dir() else []

    def add_icons(self, *names):
        (self.theme / "icons").mkdir(exist_ok=True)
        for name in names:
            (self.theme / "icons" / name).write_bytes(FAKE_PNG)

    def test_a_push_sends_every_logo(self):
        self.add_icons("com.netflix.ninja.png", "com.vidio.android.tv.PNG")
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["com.netflix.ninja.png", "com.vidio.android.tv.PNG"], self.icons_on_tv())
        self.assertEqual(FAKE_PNG, (self.device / "icons" / "com.netflix.ninja.png").read_bytes())
        self.assertIn(["write", "--uri", "content://com.gathin.tvhome.theme/icons/com.netflix.ninja.png"],
                      self.provider_calls())

    def test_a_push_without_icons_empties_the_folder(self):
        (self.device / "icons").mkdir()
        (self.device / "icons" / "com.netflix.ninja.png").write_bytes(FAKE_PNG)
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.icons_on_tv())

    def test_a_push_replaces_the_logos_of_the_theme_before(self):
        (self.device / "icons").mkdir()
        (self.device / "icons" / "com.old.app.png").write_bytes(FAKE_PNG)
        self.add_icons("com.netflix.ninja.png")
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["com.netflix.ninja.png"], self.icons_on_tv())

    def test_reset_empties_the_folder(self):
        (self.device / "icons").mkdir()
        (self.device / "icons" / "com.netflix.ninja.png").write_bytes(FAKE_PNG)
        result = self.run_theme("--reset")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.icons_on_tv())

    def test_a_name_that_is_not_a_package_is_refused_before_anything_is_sent(self):
        for name in ("netflix.png", "com.netflix.ninja.webp", "com.net-flix.png", "SOURCES.md"):
            with self.subTest(name=name):
                for old in (self.theme / "icons").glob("*") if (self.theme / "icons").is_dir() else []:
                    old.unlink()
                self.add_icons("com.netflix.ninja.png", name)
                result = self.run_theme(str(self.theme))
                self.assertEqual(2, result.returncode)
                self.assertIn("unsupported logo filename", result.stderr)
                self.assertFalse((self.device / "config.json").exists())
                self.assertFalse(self.log.exists() and self.log.read_text().strip())


class IconNamesAgreeTest(unittest.TestCase):
    def launcher_pattern(self):
        found = re.search(r'ICON = Pattern\.compile\("([^"]*)"', THEME_FILES.read_text())
        self.assertIsNotNone(found, "no ICON pattern in ThemeFiles.java")
        return found.group(1).replace("\\\\", "\\")

    def test_the_shell_script_checks_what_the_launcher_takes(self):
        sh = THEME_SH.read_text()
        self.assertIn("$DIR/icons/*.[pP][nN][gG]", sh)
        self.assertIn("$CLEAR_ICONS; printf", sh, "--reset does not empty icons/")
        pattern = self.launcher_pattern().replace("(png)", "([pP][nN][gG])")
        self.assertIn(f'"$NAME" =~ ^{pattern}$', sh)

    def test_the_windows_twin_checks_what_the_launcher_takes(self):
        ps1 = THEME_PS1.read_text()
        self.assertIn(f"-notmatch '^{self.launcher_pattern()}$'", ps1)
        self.assertIn("$dir/icons/*.[pP][nN][gG]", ps1)
        self.assertIn("$clearIcons; printf", ps1, "-Reset does not empty icons/")


if __name__ == "__main__":
    unittest.main()
