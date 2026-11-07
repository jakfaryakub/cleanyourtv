#!/usr/bin/env python3
"""Make a theme's wallpaper.webp (config.json "wallpaper_animated") from a video or GIF, and check one.

Usage:
    tools/webp_wallpaper.py <video|gif> <out.webp> [--start 0] [--seconds 8] [--fps 15] [--width 960]
                            [--height 540] [--quality 70]
    tools/webp_wallpaper.py --check <file.webp>

    tools/webp_wallpaper.py clip.mp4 work/theme-ref/wallpaper.webp --start 3 --seconds 6
    tools/webp_wallpaper.py --check work/theme-ref/wallpaper.webp

The clip is cut at --start for --seconds, resampled to --fps, scaled to cover --width x --height and cropped to it,
then encoded as a lossy animated WebP that loops forever (ffmpeg's libwebp_anim). The result is checked against
the limits the launcher's Phase 4a survey set, and so is any file given to --check:

    at most 960x540, at most 20 fps (no frame shorter than 50 ms), a loop of at most 10 s, at most 3 MB.

The launcher itself refuses only a file over 3 MB or over 1920x1080 (AnimatedWallpaper); frame rate and loop
length cost CPU on every frame, and only this check sees them. Keep a still wallpaper.jpg beside it: Home shows
that first, and whenever the animation does not play.

Needs ffmpeg with libwebp (`brew install ffmpeg`, `apt install ffmpeg`) to convert; --check needs nothing.

Exit status: 0 the file was written and is within the limits; 1 ffmpeg failed, or the file breaks a limit (each one
is named); 2 nothing was done (a bad option, an input that is not a file, an output not named .webp, no ffmpeg with
libwebp_anim on PATH, or a --check file that is not an animated WebP).
"""
import argparse
import shutil
import struct
import subprocess
import sys
from pathlib import Path

MAX_WIDTH, MAX_HEIGHT = 960, 540
MAX_FPS = 20
MIN_FRAME_MS = 1000 // MAX_FPS   # 50 ms
MAX_LOOP_MS = 10_000
MAX_BYTES = 3 * 1024 * 1024


def read_webp(data):
    """(width, height, [frame durations in ms]) of an animated WebP, or None for anything else."""
    if len(data) < 30 or data[:4] != b"RIFF" or data[8:12] != b"WEBP" or data[12:16] != b"VP8X":
        return None
    flags = data[20]
    if not flags & 0x02:   # the animation flag
        return None
    width = 1 + int.from_bytes(data[24:27], "little")
    height = 1 + int.from_bytes(data[27:30], "little")
    durations, pos = [], 12
    while pos + 8 <= len(data):
        kind, size = data[pos:pos + 4], struct.unpack("<I", data[pos + 4:pos + 8])[0]
        if kind == b"ANMF" and size >= 16:
            durations.append(int.from_bytes(data[pos + 8 + 12:pos + 8 + 15], "little"))
        pos += 8 + size + (size & 1)
    return (width, height, durations) if durations else None


def problems(size_bytes, info):
    """The limits the file breaks, one sentence each; empty when it is within all of them."""
    width, height, durations = info
    found = []
    if width > MAX_WIDTH or height > MAX_HEIGHT:
        found.append(f"{width}x{height} is over {MAX_WIDTH}x{MAX_HEIGHT}")
    short = [d for d in durations if d < MIN_FRAME_MS]
    if short:
        found.append(f"{len(short)} frame(s) shorter than {MIN_FRAME_MS} ms, faster than {MAX_FPS} fps")
    loop = sum(durations)
    if loop > MAX_LOOP_MS:
        found.append(f"the loop is {loop / 1000:.1f} s, over {MAX_LOOP_MS // 1000} s")
    if size_bytes > MAX_BYTES:
        found.append(f"{size_bytes:,} bytes is over {MAX_BYTES:,} (3 MB)")
    return found


def describe(size_bytes, info):
    width, height, durations = info
    loop = sum(durations)
    fps = len(durations) * 1000 / loop if loop else 0
    return f"{width}x{height}, {len(durations)} frames, {fps:.1f} fps, loop {loop / 1000:.1f} s, {size_bytes:,} bytes"


def check(path):
    """Prints the file's numbers and any limit it breaks. Returns the exit status."""
    data = path.read_bytes()
    info = read_webp(data)
    if info is None:
        print(f"{path} is not an animated WebP.", file=sys.stderr)
        return 2
    print(f"{path}: {describe(len(data), info)}")
    found = problems(len(data), info)
    for problem in found:
        print(f"  over the limit: {problem}", file=sys.stderr)
    return 1 if found else 0


def has_libwebp_anim(ffmpeg):
    result = subprocess.run([ffmpeg, "-hide_banner", "-encoders"], capture_output=True, text=True)
    return "libwebp_anim" in result.stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description="Make or check a theme's animated wallpaper.webp.")
    parser.add_argument("paths", nargs="+", metavar="input output | file")
    parser.add_argument("--check", action="store_true", help="only check an existing .webp against the limits")
    parser.add_argument("--start", type=float, default=0.0, help="seconds into the clip to start (default 0)")
    parser.add_argument("--seconds", type=float, default=8.0, help="length of the loop, at most 10 (default 8)")
    parser.add_argument("--fps", type=int, default=15, help="frames per second, 1 to 20 (default 15)")
    parser.add_argument("--width", type=int, default=MAX_WIDTH, help=f"at most {MAX_WIDTH} (default {MAX_WIDTH})")
    parser.add_argument("--height", type=int, default=MAX_HEIGHT, help=f"at most {MAX_HEIGHT} (default {MAX_HEIGHT})")
    parser.add_argument("--quality", type=int, default=70, help="lossy quality 1-100 (default 70)")
    args = parser.parse_args(argv)

    if args.check:
        if len(args.paths) != 1 or not Path(args.paths[0]).is_file():
            print("--check takes one existing .webp file. Nothing was done.", file=sys.stderr)
            return 2
        return check(Path(args.paths[0]))

    if len(args.paths) != 2:
        print("give <input> <output.webp>, or --check <file.webp>. Nothing was done.", file=sys.stderr)
        return 2
    source, out = Path(args.paths[0]), Path(args.paths[1])
    if not source.is_file():
        print(f"{source} is not a file. Nothing was done.", file=sys.stderr)
        return 2
    if out.suffix.lower() != ".webp":
        print(f"{out}: the output is a .webp (a theme's wallpaper.webp). Nothing was done.", file=sys.stderr)
        return 2
    if not (1 <= args.fps <= MAX_FPS and 0 < args.seconds <= MAX_LOOP_MS / 1000 and args.start >= 0
            and 0 < args.width <= MAX_WIDTH and 0 < args.height <= MAX_HEIGHT and 1 <= args.quality <= 100):
        print(f"--fps takes 1-{MAX_FPS}, --seconds up to {MAX_LOOP_MS // 1000}, --width up to {MAX_WIDTH}, "
              f"--height up to {MAX_HEIGHT}, --quality 1-100. Nothing was done.", file=sys.stderr)
        return 2
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None or not has_libwebp_anim(ffmpeg):
        print("needs ffmpeg with libwebp (brew install ffmpeg, apt install ffmpeg). Nothing was done.",
              file=sys.stderr)
        return 2

    w, h = args.width, args.height
    # Even sizes keep the encoder from rounding one side; cover then crop, as Home's CENTER_CROP shows it.
    video = (f"fps={args.fps},scale={w}:{h}:force_original_aspect_ratio=increase:flags=lanczos,"
             f"crop={w}:{h}")
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{args.start}", "-t", f"{args.seconds}",
               "-i", str(source), "-an", "-vf", video, "-c:v", "libwebp_anim", "-lossless", "0",
               "-quality", str(args.quality), "-compression_level", "6", "-loop", "0", str(out)]
    out.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0 or not out.is_file():
        print(f"ffmpeg failed: {result.stderr.strip()}", file=sys.stderr)
        return 1
    return check(out)


if __name__ == "__main__":
    sys.exit(main())
