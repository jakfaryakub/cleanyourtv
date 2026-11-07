"""tools/webp_wallpaper.py makes and checks a theme's wallpaper.webp against the Phase 4a limits.

The WebP reader and the limits run anywhere, on WebP files built here byte by byte. The conversion needs ffmpeg with
libwebp_anim and is skipped without it.

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
TOOL = ROOT / "tools" / "webp_wallpaper.py"
SPEC = importlib.util.spec_from_file_location("webp_wallpaper", TOOL)
webp = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(webp)


def chunk(kind, body):
    return kind + struct.pack("<I", len(body)) + body + (b"\0" if len(body) & 1 else b"")


def animated(width, height, durations, animation_flag=True):
    """A minimal animated WebP: VP8X, ANIM and one ANMF per frame (frame payloads left empty)."""
    vp8x = bytes([0x02 if animation_flag else 0, 0, 0, 0]) + (width - 1).to_bytes(3, "little") \
        + (height - 1).to_bytes(3, "little")
    body = chunk(b"VP8X", vp8x) + chunk(b"ANIM", b"\0" * 6)
    for ms in durations:
        body += chunk(b"ANMF", b"\0" * 12 + ms.to_bytes(3, "little") + b"\0")
    return b"RIFF" + struct.pack("<I", 4 + len(body)) + b"WEBP" + body


def run(*args):
    return subprocess.run([sys.executable, "-I", str(TOOL), *args], capture_output=True, text=True)


class ReadTest(unittest.TestCase):
    def test_size_and_frame_durations_are_read(self):
        self.assertEqual((960, 540, [66, 67, 67]), webp.read_webp(animated(960, 540, [66, 67, 67])))

    def test_a_still_webp_is_not_animated(self):
        self.assertIsNone(webp.read_webp(animated(960, 540, [66], animation_flag=False)))

    def test_other_files_are_not_webp(self):
        self.assertIsNone(webp.read_webp(b"\xff\xd8\xff\xe0" + b"\0" * 40))


class LimitTest(unittest.TestCase):
    def test_a_file_within_every_limit_has_no_problem(self):
        self.assertEqual([], webp.problems(900_000, (960, 540, [67] * 120)))   # 15 fps, 8 s

    def test_each_limit_is_named(self):
        self.assertIn("1280x720", webp.problems(1000, (1280, 720, [67]))[0])
        self.assertIn("50 ms", webp.problems(1000, (960, 540, [33, 67]))[0])
        self.assertIn("10.5 s", webp.problems(1000, (960, 540, [70] * 150))[0])
        self.assertIn("3 MB", webp.problems(webp.MAX_BYTES + 1, (960, 540, [67]))[0])

    def test_twenty_fps_and_ten_seconds_are_allowed(self):
        self.assertEqual([], webp.problems(webp.MAX_BYTES, (960, 540, [50] * 200)))

    def test_check_exits_1_over_a_limit_and_0_within(self):
        with tempfile.TemporaryDirectory() as tmp:
            good, fast = Path(tmp) / "good.webp", Path(tmp) / "fast.webp"
            good.write_bytes(animated(960, 540, [67] * 30))
            fast.write_bytes(animated(960, 540, [20] * 30))
            self.assertEqual(0, run("--check", str(good)).returncode)
            result = run("--check", str(fast))
            self.assertEqual(1, result.returncode)
            self.assertIn("over the limit", result.stderr)

    def test_check_refuses_a_file_that_is_not_an_animated_webp(self):
        with tempfile.TemporaryDirectory() as tmp:
            still = Path(tmp) / "still.webp"
            still.write_bytes(animated(960, 540, [67], animation_flag=False))
            self.assertEqual(2, run("--check", str(still)).returncode)


class RefusalTest(unittest.TestCase):
    def test_options_out_of_range_are_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "in.gif"
            source.write_bytes(b"GIF89a")
            for extra in (["--fps", "25"], ["--seconds", "12"], ["--width", "1280"], ["--quality", "0"]):
                self.assertEqual(2, run(str(source), str(Path(tmp) / "out.webp"), *extra).returncode, extra)

    def test_the_output_is_a_webp(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "in.gif"
            source.write_bytes(b"GIF89a")
            self.assertEqual(2, run(str(source), str(Path(tmp) / "out.gif")).returncode)


def ffmpeg_webp():
    path = shutil.which("ffmpeg")
    return path is not None and webp.has_libwebp_anim(path)


@unittest.skipUnless(ffmpeg_webp(), "needs ffmpeg with libwebp_anim")
class ConvertTest(unittest.TestCase):
    def test_a_clip_becomes_a_looping_webp_within_the_limits(self):
        with tempfile.TemporaryDirectory() as tmp:
            clip = Path(tmp) / "clip.mp4"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                            "testsrc2=s=1280x720:r=30:d=2", "-pix_fmt", "yuv420p", str(clip)], check=True)
            out = Path(tmp) / "wallpaper.webp"
            result = run(str(clip), str(out), "--seconds", "2", "--fps", "10", "--width", "480", "--height", "270")
            self.assertEqual(0, result.returncode, result.stderr)
            width, height, durations = webp.read_webp(out.read_bytes())
            self.assertEqual((480, 270), (width, height))
            self.assertAlmostEqual(2000, sum(durations), delta=150)
