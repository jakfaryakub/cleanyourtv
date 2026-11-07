"""tools/theme.sh and theme.ps1 push a theme's fonts/ and empty it again, and the launcher takes the same names.

A theme can name a font file in config.json ("font"); the file lives in the theme's fonts/ folder and is never
bundled in the APK. These tests run the real tools/theme.sh against the stub TV of
tests/test_theme_push_ownership.py: a push sends only the font the key names (a fonts/ folder may hold
alternatives), empties the folder on the TV first so a font from the theme before does not linger, sends none
without the key, refuses a name it cannot push or a file that is not there before anything is sent, and --reset
empties the folder too. The last checks read ThemeFiles.java and theme.ps1, so the launcher's provider and the Windows
twin accept the same suffixes the shell script sends.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
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

FAKE_FONT = b"\x00\x01\x00\x00" + b"\x00" * 60


class ThemeFontPushTest(unittest.TestCase):
    setUp = harness.ThemePushTest.setUp
    run_theme = harness.ThemePushTest.run_theme

    def fonts_on_tv(self):
        folder = self.device / "fonts"
        return sorted(p.name for p in folder.iterdir()) if folder.is_dir() else []

    def name_font(self, name):
        config = json.loads((self.theme / "config.json").read_text())
        config["font"] = name
        (self.theme / "config.json").write_text(json.dumps(config))

    def test_a_push_sends_only_the_font_the_key_names(self):
        (self.theme / "fonts").mkdir()
        (self.theme / "fonts" / "plus-jakarta-sans.ttf").write_bytes(FAKE_FONT)
        (self.theme / "fonts" / "inter.ttf").write_bytes(FAKE_FONT)
        (self.theme / "fonts" / "plus-jakarta-sans.OFL.txt").write_text("licence, not a font")
        self.name_font("plus-jakarta-sans.ttf")
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["plus-jakarta-sans.ttf"], self.fonts_on_tv())
        self.assertEqual(FAKE_FONT, (self.device / "fonts" / "plus-jakarta-sans.ttf").read_bytes())

    def test_a_push_without_the_key_sends_no_font_and_empties_the_folder(self):
        (self.theme / "fonts").mkdir()
        (self.theme / "fonts" / "inter.ttf").write_bytes(FAKE_FONT)
        (self.device / "fonts").mkdir()
        (self.device / "fonts" / "manrope.ttf").write_bytes(FAKE_FONT)
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.fonts_on_tv())

    def test_a_key_with_no_file_behind_it_is_refused_before_anything_is_sent(self):
        self.name_font("missing.ttf")
        result = self.run_theme(str(self.theme))
        self.assertEqual(2, result.returncode)
        self.assertIn("is not there", result.stderr)
        self.assertFalse((self.device / "config.json").exists())

    def test_a_push_without_fonts_empties_the_folder(self):
        (self.device / "fonts").mkdir()
        (self.device / "fonts" / "inter.ttf").write_bytes(FAKE_FONT)
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.fonts_on_tv())

    def test_reset_empties_the_folder(self):
        (self.device / "fonts").mkdir()
        (self.device / "fonts" / "manrope.otf").write_bytes(FAKE_FONT)
        result = self.run_theme("--reset")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.fonts_on_tv())

    def test_a_name_it_cannot_push_is_refused_before_anything_is_sent(self):
        (self.theme / "fonts").mkdir()
        (self.theme / "fonts" / "Inter[wght].ttf").write_bytes(FAKE_FONT)
        self.name_font("Inter[wght].ttf")
        result = self.run_theme(str(self.theme))
        self.assertEqual(2, result.returncode)
        self.assertIn("unsupported font filename", result.stderr)
        self.assertFalse((self.device / "config.json").exists())
        self.assertFalse(self.log.exists() and self.log.read_text().strip())


class FontSuffixesAgreeTest(unittest.TestCase):
    def launcher_suffixes(self):
        found = re.search(r'FONT = Pattern\.compile\("[^"]*\\\\\.\(([a-z|]+)\)"', THEME_FILES.read_text())
        self.assertIsNotNone(found, "no FONT pattern in ThemeFiles.java")
        return set(found.group(1).split("|"))

    def test_the_shell_script_sends_what_the_launcher_takes(self):
        sh = THEME_SH.read_text()
        key_check = re.search(r'FONT_KEY" =~ \^[^ ]*\\\.\(([^)]*)\)\$', sh)
        self.assertIsNotNone(key_check, "theme.sh does not check the font key's suffix")
        for suffix in self.launcher_suffixes():
            glob = "".join(f"[{c.lower()}{c.upper()}]" for c in suffix)
            self.assertIn(f"$DIR/fonts/*.{glob}", sh, f"theme.sh does not clear .{suffix} fonts")
            self.assertIn(glob, key_check.group(1).split("|"), f"theme.sh does not send .{suffix} fonts")

    def test_the_windows_twin_sends_what_the_launcher_takes(self):
        found = re.search(r"\$fontKey -notmatch '\^[^']*\\\.\(([a-z|]+)\)\$'", THEME_PS1.read_text())
        self.assertIsNotNone(found, "theme.ps1 has no font suffix filter")
        self.assertEqual(self.launcher_suffixes(), set(found.group(1).split("|")))


if __name__ == "__main__":
    unittest.main()
