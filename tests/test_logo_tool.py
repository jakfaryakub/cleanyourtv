"""tools/logo.py turns a wordmark into a theme logo, icons/<package>.png, the name the launcher and theme.sh take.

The refusals run anywhere. The renders need rsvg-convert and are skipped without it: a wide logo is capped at
--max-width, a square one gets the full --height, a PNG source is scaled the same way, and the result keeps its
transparency (an RGBA PNG; cairo writes a fully opaque picture as RGB, so each test logo has a transparent part). --fill paints every colour but `none` in one colour, for a dark wordmark.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "logo.py"
SPEC = importlib.util.spec_from_file_location("logo_tool", TOOL)
logo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(logo)

WIDE_SVG = ('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="100" viewBox="0 0 400 100">'
            '<rect width="300" height="100" fill="#d81f26"/></svg>')   # a quarter left transparent
SQUARE_SVG = ('<svg xmlns="http://www.w3.org/2000/svg" width="50" height="50" viewBox="0 0 50 50">'
              '<circle cx="25" cy="25" r="20" style="fill:#0779ff;stroke:none"/></svg>')


def run(*args):
    return subprocess.run([sys.executable, "-I", str(TOOL), *args], capture_output=True, text=True)


def png_info(path):
    data = Path(path).read_bytes()
    width, height = struct.unpack(">II", data[16:24])
    return width, height, data[25]   # colour type 6 is RGBA


class RefusalTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        self.svg = self.tmp / "logo.svg"
        self.svg.write_text(WIDE_SVG)

    def test_a_name_that_is_not_a_package_is_refused(self):
        for name in ("netflix", "com.net-flix", "1com.netflix", "com..netflix"):
            with self.subTest(name=name):
                result = run(str(self.svg), name, "--out", str(self.tmp / "icons"))
                self.assertEqual(2, result.returncode)
                self.assertIn("not a package name", result.stderr)
        self.assertFalse((self.tmp / "icons").exists())

    def test_a_source_that_is_not_svg_or_png_is_refused(self):
        source = self.tmp / "logo.jpg"
        source.write_bytes(b"\xff\xd8")
        result = run(str(source), "com.netflix.ninja", "--out", str(self.tmp / "icons"))
        self.assertEqual(2, result.returncode)

    def test_fill_on_a_png_is_refused(self):
        source = self.tmp / "logo.png"
        source.write_bytes(logo.PNG_SIGNATURE + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", 4, 2) + b"\x08\x06\x00\x00\x00")
        result = run(str(source), "com.netflix.ninja", "--fill", "#FFFFFF", "--out", str(self.tmp / "icons"))
        self.assertEqual(2, result.returncode)
        self.assertIn("SVG source only", result.stderr)

    def test_fill_takes_a_colour_only(self):
        result = run(str(self.svg), "com.netflix.ninja", "--fill", "white;x", "--out", str(self.tmp / "icons"))
        self.assertEqual(2, result.returncode)


class RecolourTest(unittest.TestCase):
    def test_every_colour_but_none_is_replaced(self):
        out = logo.recolour(SQUARE_SVG + '<path fill="none" stroke="#000"/><g style="fill: red; fill-rule:evenodd"/>',
                            "#FFFFFF")
        self.assertIn("fill:#FFFFFF;stroke:none", out)
        self.assertIn('fill="none" stroke="#FFFFFF"', out)
        self.assertIn("fill:#FFFFFF; fill-rule:evenodd", out)
        self.assertNotIn("#0779ff", out)

    def test_the_root_gets_the_colour_once(self):
        out = logo.recolour(WIDE_SVG, "#FFFFFF")
        self.assertTrue(out.startswith('<svg fill="#FFFFFF" xmlns='), out[:60])
        self.assertEqual(1, logo.recolour(out, "#000000").count("<svg fill="))


@unittest.skipUnless(shutil.which("rsvg-convert"), "rsvg-convert is not installed")
class RenderTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        self.icons = self.tmp / "icons"

    def render(self, name, content, *extra):
        source = self.tmp / name
        if isinstance(content, bytes):
            source.write_bytes(content)
        else:
            source.write_text(content)
        result = run(str(source), "com.example.app", "--out", str(self.icons), *extra)
        self.assertEqual(0, result.returncode, result.stderr)
        return png_info(self.icons / "com.example.app.png")

    def test_a_wide_logo_is_capped_at_the_max_width(self):
        self.assertEqual((360, 90, 6), self.render("wide.svg", WIDE_SVG))

    def test_a_square_logo_gets_the_full_height(self):
        self.assertEqual((120, 120, 6), self.render("square.svg", SQUARE_SVG))

    def test_a_png_source_is_scaled_the_same_way(self):
        first = self.render("wide.svg", WIDE_SVG, "--height", "20")
        self.assertEqual((80, 20, 6), first)
        png = (self.icons / "com.example.app.png").read_bytes()
        self.assertEqual((360, 90, 6), self.render("small.png", png))

    def test_fill_renders_in_the_one_colour(self):
        self.assertEqual((120, 120, 6), self.render("square.svg", SQUARE_SVG, "--fill", "#FFFFFF"))


if __name__ == "__main__":
    unittest.main()
