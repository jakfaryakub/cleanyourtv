#!/usr/bin/env python3
"""Turn an app's wordmark (SVG or PNG) into a theme logo: icons/<package>.png, for config.json "app_logos".

Usage:
    tools/logo.py <source.svg|.png> <package> [--out DIR] [--height 120] [--max-width 360] [--fill '#FFFFFF']

    tools/logo.py work/theme-ref/logo-src/netflix-2015.svg com.netflix.ninja
    tools/logo.py work/theme-ref/logo-src/prime-video-2024.svg com.amazon.amazonvideo.livingroom --fill '#FFFFFF'

The logo is rendered with its transparency kept, `--height` px high (120 by default); a logo that would then be
wider than `--max-width` (360) is rendered at that width instead, a little lower. `--fill` paints every coloured
part of an SVG in one colour, for a dark wordmark that would vanish on a dark tile. The file goes to
`--out` (work/theme-ref/icons by default, which git ignores) as <package>.png, the name tools/theme.sh and the
launcher take.

Needs `rsvg-convert` (librsvg: `brew install librsvg`, `apt install librsvg2-bin`). A PNG source is wrapped in a
small SVG and rendered the same way, so no Python imaging library is needed. Logos are trademarks: keep the
sources and the results in your own theme folder, not in this repository.

Exit status: 0 the logo was written; 1 rsvg-convert failed or wrote something that is not a PNG; 2 nothing was
done (a package name the launcher would refuse, a source that is not an .svg or .png, --fill on a PNG, a size that
is not a positive number, or no rsvg-convert on PATH).
"""
import argparse
import base64
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "work" / "theme-ref" / "icons"
# ThemeFiles.ICON in the launcher, without the suffix: two or more Java identifiers joined by dots.
PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+")
COLOUR = re.compile(r"#[0-9A-Fa-f]{3}|#[0-9A-Fa-f]{6}")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def png_size(data):
    """Width and height from a PNG's IHDR chunk, or None for anything that is not a PNG."""
    if len(data) < 24 or not data.startswith(PNG_SIGNATURE) or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def recolour(svg, colour):
    """The SVG with every fill and stroke colour that is not none or transparent set to `colour`."""
    def keep(value):
        return value.strip().lower() in ("none", "transparent") or value.strip().startswith("url(")

    def attr(match):
        return match.group(0) if keep(match.group(3)) else f'{match.group(1)}={match.group(2)}{colour}{match.group(2)}'

    def prop(match):
        return match.group(0) if keep(match.group(2)) else f"{match.group(1)}:{colour}"

    svg = re.sub(r'\b(fill|stroke|stop-color)=(["\'])(.*?)\2', attr, svg)
    svg = re.sub(r'\b(fill|stroke|stop-color)\s*:\s*([^;"\'}]+)', prop, svg)
    # Shapes with no fill of their own are drawn black: give the root the colour, which they inherit.
    root = re.search(r"<svg\b[^>]*>", svg)
    if root and not re.search(r"\sfill=", root.group(0)):
        svg = svg[:root.start()] + f'<svg fill="{colour}"' + svg[root.start() + 4:]
    return svg


def wrap_png(data):
    """A PNG as an SVG of the same size, so rsvg-convert can scale it."""
    width, height = png_size(data)
    encoded = base64.b64encode(data).decode("ascii")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
            f'width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
            f'<image width="{width}" height="{height}" xlink:href="data:image/png;base64,{encoded}"/></svg>')


def render(rsvg, svg_path, out_path, height, max_width):
    """Renders at `height`; if that is wider than `max_width`, again at that width. Returns (width, height)."""
    for size_args in (["-h", str(height)], ["-w", str(max_width)]):
        subprocess.run([rsvg, *size_args, "--keep-aspect-ratio", "-f", "png", "-o", str(out_path), str(svg_path)],
                       check=True, capture_output=True)
        size = png_size(out_path.read_bytes())
        if size is None:
            raise ValueError("rsvg-convert did not write a PNG")
        if size[0] <= max_width:
            return size
    return size


def main(argv=None):
    parser = argparse.ArgumentParser(description="Make icons/<package>.png for a theme's app_logos.")
    parser.add_argument("source", type=Path)
    parser.add_argument("package")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--height", type=int, default=120)
    parser.add_argument("--max-width", type=int, default=360)
    parser.add_argument("--fill", help="one colour for the whole logo, e.g. '#FFFFFF' (SVG only)")
    args = parser.parse_args(argv)

    if not PACKAGE.fullmatch(args.package):
        print(f"not a package name: {args.package} (e.g. com.netflix.ninja). Nothing was written.", file=sys.stderr)
        return 2
    if args.height <= 0 or args.max_width <= 0:
        print("--height and --max-width take a positive number of px. Nothing was written.", file=sys.stderr)
        return 2
    if args.fill is not None and not COLOUR.fullmatch(args.fill):
        print(f"--fill takes a colour like '#FFFFFF', not {args.fill}. Nothing was written.", file=sys.stderr)
        return 2
    suffix = args.source.suffix.lower()
    if suffix not in (".svg", ".png") or not args.source.is_file():
        print(f"{args.source} is not an .svg or .png file. Nothing was written.", file=sys.stderr)
        return 2
    if suffix == ".png" and args.fill:
        print("--fill works on an SVG source only. Nothing was written.", file=sys.stderr)
        return 2
    rsvg = shutil.which("rsvg-convert")
    if rsvg is None:
        print("rsvg-convert is not on PATH (brew install librsvg, apt install librsvg2-bin). Nothing was written.",
              file=sys.stderr)
        return 2

    if suffix == ".png":
        data = args.source.read_bytes()
        if png_size(data) is None:
            print(f"{args.source} is not a PNG. Nothing was written.", file=sys.stderr)
            return 2
        svg = wrap_png(data)
    else:
        svg = args.source.read_text(encoding="utf-8")
        if args.fill:
            svg = recolour(svg, args.fill)

    args.out.mkdir(parents=True, exist_ok=True)
    out_path = args.out / f"{args.package}.png"
    with tempfile.TemporaryDirectory() as tmp:
        svg_path = Path(tmp) / "logo.svg"
        svg_path.write_text(svg, encoding="utf-8")
        try:
            width, height = render(rsvg, svg_path, out_path, args.height, args.max_width)
        except (subprocess.CalledProcessError, ValueError) as error:
            detail = getattr(error, "stderr", b"") or b""
            print(f"rsvg-convert failed for {args.source}: {detail.decode(errors='replace').strip() or error}",
                  file=sys.stderr)
            return 1
    print(f"{out_path} {width}x{height}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
