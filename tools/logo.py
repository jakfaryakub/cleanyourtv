#!/usr/bin/env python3
"""Turn an app's wordmark (SVG or PNG) into a theme logo: icons/<package>.png, for config.json "app_logos".

Usage:
    tools/logo.py <source.svg|.png> <package> [--out DIR] [--height 120] [--max-width 360] [--fill '#FFFFFF']
                  [--shadow [--shadow-blur 8] [--shadow-opacity 0.85]]
    tools/logo.py --icon <material symbol> --text "<Name>" <package> [--font FILE] [--out DIR] [--height 120] ...

    tools/logo.py work/theme-ref/logo-src/netflix-2015.svg com.netflix.ninja
    tools/logo.py work/theme-ref/logo-src/prime-video-2024.svg com.amazon.amazonvideo.livingroom --fill '#FFFFFF'
    tools/logo.py --icon settings --text "Settings" com.android.tv.settings

`--icon` makes a logo for a system app that has no wordmark: a white Material Symbol from tools/material-symbols/
(Apache 2.0, see its README) with `--text` beside it in white, in the theme's font. `--font` is a .ttf/.otf file;
by default the one work/theme-ref/config.json names in its "font" key, from work/theme-ref/fonts/. The icon is
`--height` high, so the logo stands as high as a wordmark made with the same --height; a long name is set smaller
first (down to 0.36 of the height), and only text still too wide for --max-width scales the whole logo down. The text is drawn by rsvg-convert through a
private fontconfig that holds only that font file.

The logo is rendered with its transparency kept, `--height` px high (120 by default); a logo that would then be
wider than `--max-width` (360) is rendered at that width instead, a little lower. `--fill` paints every coloured
part of an SVG in one colour, for a dark wordmark that would vanish on a dark tile. `--shadow` bakes a soft dark
shadow under the logo, for a logo drawn with no card (tile_style "logo") over a light wallpaper. The logo keeps
the size it has without the shadow; the shadow sits in a transparent margin around it, outside --height and
--max-width, and the PNG names that margin in a tEXt chunk (SHADOW_KEY) so the launcher sizes the logo, not the
margin. The launcher draws it at no extra cost.
The file goes to
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
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "work" / "theme-ref" / "icons"
THEME_REF = ROOT / "work" / "theme-ref"
SYMBOLS = ROOT / "tools" / "material-symbols"
ICON_NAME = re.compile(r"[a-z0-9_]+")
# --icon layout, as fractions of the logo's height: the text's size, the smallest it shrinks to before the whole
# logo is scaled down to fit --max-width, and the gap between icon and text.
TEXT_SIZE = 0.5
TEXT_SIZE_MIN = 0.36
TEXT_GAP = 0.2
MEASURE_SIZE, MEASURE_BASELINE = 100, 150   # the text is measured at this size, on this baseline, in px
# ThemeFiles.ICON in the launcher, without the suffix: two or more Java identifiers joined by dots.
PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+")
COLOUR = re.compile(r"#[0-9A-Fa-f]{3}|#[0-9A-Fa-f]{6}")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SHADOW_DROP = 3   # px the shadow sits below the logo
# The tEXt keyword that tells ThemeIcons how wide the shadow's margin is, in px of the PNG.
SHADOW_KEY = b"cleanyourtv-shadow-margin"


def png_size(data):
    """Width and height from a PNG's IHDR chunk, or None for anything that is not a PNG."""
    if len(data) < 24 or not data.startswith(PNG_SIGNATURE) or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def png_pixels(data):
    """(width, height, rows of RGBA bytes) for an 8-bit RGBA, non-interlaced PNG as rsvg-convert writes it."""
    width, height = png_size(data)
    if data[24] != 8 or data[25] != 6 or data[28] != 0:
        raise ValueError("not an 8-bit RGBA PNG")
    idat, pos = b"", 8
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        if kind == b"IDAT":
            idat += data[pos + 8:pos + 8 + length]
        pos += 12 + length
    raw, stride, rows, previous = zlib.decompress(idat), width * 4, [], bytearray(width * 4)
    for y in range(height):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            left = line[i - 4] if i >= 4 else 0
            up, corner = previous[i], previous[i - 4] if i >= 4 else 0
            if kind == 1:
                line[i] = (line[i] + left) & 255
            elif kind == 2:
                line[i] = (line[i] + up) & 255
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 255
            elif kind == 4:
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                line[i] = (line[i] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        rows.append(line)
        previous = line
    return width, height, rows


def alpha_box(data):
    """(left, top, right, bottom) of the pixels that are not transparent, right and bottom exclusive; None if none."""
    width, height, rows = png_pixels(data)
    xs, ys = [], []
    for y, row in enumerate(rows):
        seen = [x for x in range(width) if row[x * 4 + 3]]
        if seen:
            xs += (seen[0], seen[-1])
            ys.append(y)
    return (min(xs), ys[0], max(xs) + 1, ys[-1] + 1) if ys else None


def theme_font():
    """The font file work/theme-ref/config.json names, or None."""
    try:
        name = json.loads((THEME_REF / "config.json").read_text(encoding="utf-8")).get("font")
    except (OSError, ValueError):
        return None
    return THEME_REF / "fonts" / name if isinstance(name, str) and name else None


def private_fonts(font, tmp):
    """A fontconfig file that knows only `font`, so any family name rsvg-convert asks for resolves to it."""
    folder = Path(tmp) / "fonts"
    folder.mkdir()
    shutil.copy(font, folder / font.name)
    conf = Path(tmp) / "fonts.conf"
    conf.write_text(f'<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "fonts.dtd"><fontconfig>'
                    f'<dir>{folder}</dir><cachedir>{Path(tmp) / "cache"}</cachedir></fontconfig>', encoding="utf-8")
    return conf


def text_svg(text, size, x, y):
    escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return (f'<text x="{x:.2f}" y="{y:.2f}" font-family="theme" font-size="{size:.2f}" '
            f'fill="#FFFFFF">{escaped}</text>')


def icon_text_size(height, max_width, box):
    """The text's size: TEXT_SIZE, or smaller down to TEXT_SIZE_MIN when the logo would be wider than max_width,
    so a long name keeps the icon as high as the other logos instead of shrinking all of it."""
    room = max_width - height * (1 + TEXT_GAP)
    fits = room * MEASURE_SIZE / (box[2] - box[0])
    return max(height * TEXT_SIZE_MIN, min(height * TEXT_SIZE, fits))


def icon_logo_svg(icon_svg, text, height, box, text_size=None):
    """The icon `height` px square, white, then the text centred on it; box is the text measured by alpha_box."""
    view = re.search(r"viewBox=([\"'])(.*?)\1", icon_svg)
    paths = "".join(re.findall(r"<path\b[^>]*/>", icon_svg))
    if not view or not paths:
        raise ValueError("the icon has no viewBox or no path")
    text_size = text_size or height * TEXT_SIZE
    scale = text_size / MEASURE_SIZE
    left, top, right, bottom = box
    text_x = height * (1 + TEXT_GAP) - left * scale
    baseline = height / 2 - ((top + bottom) / 2 - MEASURE_BASELINE) * scale
    width = height * (1 + TEXT_GAP) + (right - left) * scale
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.2f}" height="{height}" '
            f'viewBox="0 0 {width:.2f} {height}">'
            f'<svg x="0" y="0" width="{height}" height="{height}" viewBox="{view.group(2)}" fill="#FFFFFF">{paths}</svg>'
            f'{text_svg(text, text_size, text_x, baseline)}</svg>')


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


def shadow_margin(blur, dy):
    """Room the shadow needs on each side: three blur radii, plus the drop below."""
    return int(blur * 3 + 0.999) + dy


def with_shadow(data, blur, opacity, dy):
    """The rendered logo, as an SVG on a canvas widened by the margin, over a blurred dark copy of itself."""
    width, height = png_size(data)
    margin = shadow_margin(blur, dy)
    encoded = base64.b64encode(data).decode("ascii")
    image = f'width="{width}" height="{height}" xlink:href="data:image/png;base64,{encoded}"'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
            f'width="{width + 2 * margin}" height="{height + 2 * margin}">'
            f'<filter id="shadow" x="-50%" y="-50%" width="200%" height="200%">'
            f'<feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 {opacity} 0"/>'
            f'<feGaussianBlur stdDeviation="{blur}"/></filter>'
            f'<image x="{margin}" y="{margin + dy}" {image} filter="url(#shadow)"/>'
            f'<image x="{margin}" y="{margin}" {image}/></svg>')


def with_margin_chunk(data, margin):
    """The PNG with a tEXt chunk right after IHDR naming the shadow's margin, for ThemeIcons."""
    body = SHADOW_KEY + b"\x00" + str(margin).encode("ascii")
    chunk = struct.pack(">I", len(body)) + b"tEXt" + body + struct.pack(">I", zlib.crc32(b"tEXt" + body) & 0xFFFFFFFF)
    ihdr_end = 8 + 4 + 4 + 13 + 4
    return data[:ihdr_end] + chunk + data[ihdr_end:]


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
    parser.add_argument("names", nargs="+", metavar="source package | package")
    parser.add_argument("--icon", help="a Material Symbol from tools/material-symbols/, e.g. settings")
    parser.add_argument("--text", help="with --icon: the name drawn beside the icon")
    parser.add_argument("--font", type=Path, help="with --icon: the .ttf/.otf for the text (default: the theme's)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--height", type=int, default=120)
    parser.add_argument("--max-width", type=int, default=360)
    parser.add_argument("--fill", help="one colour for the whole logo, e.g. '#FFFFFF' (SVG only)")
    parser.add_argument("--shadow", action="store_true", help="bake a soft dark shadow under the logo")
    parser.add_argument("--shadow-blur", type=int, default=8, help="the shadow's blur radius in px (1-30)")
    parser.add_argument("--shadow-opacity", type=float, default=0.85, help="the shadow's opacity (0.1-1)")
    args = parser.parse_args(argv)
    if args.icon is not None:
        return icon_main(args)
    if len(args.names) != 2 or args.text is not None or args.font is not None:
        print("give <source> <package>, or --icon NAME --text TEXT <package>. Nothing was written.", file=sys.stderr)
        return 2
    args.source, args.package = Path(args.names[0]), args.names[1]

    if not PACKAGE.fullmatch(args.package):
        print(f"not a package name: {args.package} (e.g. com.netflix.ninja). Nothing was written.", file=sys.stderr)
        return 2
    if args.height <= 0 or args.max_width <= 0:
        print("--height and --max-width take a positive number of px. Nothing was written.", file=sys.stderr)
        return 2
    if args.fill is not None and not COLOUR.fullmatch(args.fill):
        print(f"--fill takes a colour like '#FFFFFF', not {args.fill}. Nothing was written.", file=sys.stderr)
        return 2
    if not 1 <= args.shadow_blur <= 30 or not 0.1 <= args.shadow_opacity <= 1:
        print("--shadow-blur takes 1 to 30 px and --shadow-opacity 0.1 to 1. Nothing was written.", file=sys.stderr)
        return 2
    margin = shadow_margin(args.shadow_blur, SHADOW_DROP) if args.shadow else 0
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
            if args.shadow:
                shadowed = Path(tmp) / "shadow.svg"
                shadowed.write_text(with_shadow(out_path.read_bytes(), args.shadow_blur, args.shadow_opacity,
                                                SHADOW_DROP), encoding="utf-8")
                subprocess.run([rsvg, "-f", "png", "-o", str(out_path), str(shadowed)], check=True, capture_output=True)
                out_path.write_bytes(with_margin_chunk(out_path.read_bytes(), margin))
        except (subprocess.CalledProcessError, ValueError) as error:
            detail = getattr(error, "stderr", b"") or b""
            print(f"rsvg-convert failed for {args.source}: {detail.decode(errors='replace').strip() or error}",
                  file=sys.stderr)
            return 1
    print(f"{out_path} {width}x{height}" + (f" + {margin} px shadow margin" if margin else ""))
    return 0


def icon_main(args):
    """--icon NAME --text TEXT <package>: a white Material Symbol with the name beside it in the theme font."""
    if len(args.names) != 1 or not args.text or not args.text.strip():
        print("--icon takes --text and one package name. Nothing was written.", file=sys.stderr)
        return 2
    package = args.names[0]
    if not PACKAGE.fullmatch(package):
        print(f"not a package name: {package} (e.g. com.netflix.ninja). Nothing was written.", file=sys.stderr)
        return 2
    icon = SYMBOLS / f"{args.icon}.svg"
    if not ICON_NAME.fullmatch(args.icon) or not icon.is_file():
        print(f"no tools/material-symbols/{args.icon}.svg (see its README to add one). Nothing was written.",
              file=sys.stderr)
        return 2
    font = args.font or theme_font()
    if font is None or font.suffix.lower() not in (".ttf", ".otf") or not font.is_file():
        print(f"no font file: {font or 'work/theme-ref/config.json names none'} (give --font). Nothing was written.",
              file=sys.stderr)
        return 2
    if args.height <= 0 or args.max_width <= 0 or args.fill is not None:
        print("--height and --max-width take a positive number of px; --icon is always white. Nothing was written.",
              file=sys.stderr)
        return 2
    if not 1 <= args.shadow_blur <= 30 or not 0.1 <= args.shadow_opacity <= 1:
        print("--shadow-blur takes 1 to 30 px and --shadow-opacity 0.1 to 1. Nothing was written.", file=sys.stderr)
        return 2
    rsvg = shutil.which("rsvg-convert")
    if rsvg is None:
        print("rsvg-convert is not on PATH (brew install librsvg, apt install librsvg2-bin). Nothing was written.",
              file=sys.stderr)
        return 2
    margin = shadow_margin(args.shadow_blur, SHADOW_DROP) if args.shadow else 0
    args.out.mkdir(parents=True, exist_ok=True)
    out_path = args.out / f"{package}.png"
    with tempfile.TemporaryDirectory() as tmp:
        # Pango on macOS asks CoreText for fonts unless told to use fontconfig, which holds only this font.
        saved = {key: os.environ.get(key) for key in ("FONTCONFIG_FILE", "PANGOCAIRO_BACKEND")}
        os.environ["FONTCONFIG_FILE"] = str(private_fonts(font, tmp))
        os.environ["PANGOCAIRO_BACKEND"] = "fontconfig"
        try:
            measure = Path(tmp) / "measure.svg"
            wide = MEASURE_SIZE * (len(args.text) + 2)
            measure.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{wide}" height="200">'
                               f'{text_svg(args.text, MEASURE_SIZE, 0, MEASURE_BASELINE)}</svg>', encoding="utf-8")
            measured = Path(tmp) / "measure.png"
            subprocess.run([rsvg, "-f", "png", "-o", str(measured), str(measure)], check=True, capture_output=True)
            box = alpha_box(measured.read_bytes())
            if box is None:
                raise ValueError("the text drew nothing (does the font have these letters?)")
            svg_path = Path(tmp) / "logo.svg"
            size = icon_text_size(args.height, args.max_width, box)
            svg_path.write_text(icon_logo_svg(icon.read_text(encoding="utf-8"), args.text, args.height, box, size),
                                encoding="utf-8")
            width, height = render(rsvg, svg_path, out_path, args.height, args.max_width)
            if args.shadow:
                shadowed = Path(tmp) / "shadow.svg"
                shadowed.write_text(with_shadow(out_path.read_bytes(), args.shadow_blur, args.shadow_opacity,
                                                SHADOW_DROP), encoding="utf-8")
                subprocess.run([rsvg, "-f", "png", "-o", str(out_path), str(shadowed)], check=True, capture_output=True)
                out_path.write_bytes(with_margin_chunk(out_path.read_bytes(), margin))
        except (subprocess.CalledProcessError, ValueError) as error:
            detail = getattr(error, "stderr", b"") or b""
            print(f"rsvg-convert failed for --icon {args.icon}: {detail.decode(errors='replace').strip() or error}",
                  file=sys.stderr)
            return 1
        finally:
            for key, value in saved.items():
                if value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = value
    print(f"{out_path} {width}x{height}" + (f" + {margin} px shadow margin" if margin else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
