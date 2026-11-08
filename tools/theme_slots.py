#!/usr/bin/env python3
"""Helpers for tools/theme.sh and theme.ps1 --slot, --use, --list and --delete (docs/PLAN.md, Phase 3).

They read what the TV answered on stdin and print what the script needs; nothing here talks to the TV.

  theme_slots.py list                          < query     the themes on the TV, as a table
  theme_slots.py use <id>                      < query     exit 0 when <id> can be switched to, else a message, 1
  theme_slots.py delete <slug>                 < query     exit 0 when <slug> is there and not in use, else 1
  theme_slots.py check <folder> <slug> <font>  < query, a line "--df--", then `df -k /sdcard`
                                                           exit 0 when the theme fits the limits, else a message, 1
  theme_slots.py size <folder> <font>                      the bytes a push sends

<query> is the output of `content query --uri content://com.gathin.tvhome.theme/themes`.
"""
import re
import sys
from pathlib import Path

MAX_SLOTS = 8
MAX_THEME = 8 * 1024 * 1024
MAX_TOTAL = 40 * 1024 * 1024
ROW = re.compile(r"Row: \d+ id=(.*?), name=(.*), bytes=(\d+), animated=(\d), usable=(\d), active=(\d)\s*$")
IMAGE = re.compile(r"[A-Za-z0-9_.-]+\.(jpg|jpeg|png|webp)$", re.IGNORECASE)


def rows(text):
    """The themes the launcher listed: dicts with id, name, bytes, animated, usable, active."""
    out = []
    for line in text.splitlines():
        m = ROW.match(line.strip())
        if m:
            out.append({"id": m.group(1), "name": m.group(2), "bytes": int(m.group(3)),
                        "animated": m.group(4) == "1", "usable": m.group(5) == "1", "active": m.group(6) == "1"})
    return out


def mb(n):
    return f"{n / (1024 * 1024):.1f} MB"


def theme_size(folder, font):
    """What a push of the folder sends: the files theme.sh pushes, and only the font config.json names."""
    folder = Path(folder)
    n = 0
    for name in ("config.json", "wallpaper.jpg", "wallpaper.webp"):
        if (folder / name).is_file():
            n += (folder / name).stat().st_size
    walls = folder / "wallpapers"
    if walls.is_dir():
        for f in walls.iterdir():
            if f.is_file() and (IMAGE.match(f.name) or f.name == "captions.txt"):
                n += f.stat().st_size
    if font and (folder / "fonts" / font).is_file():
        n += (folder / "fonts" / font).stat().st_size
    icons = folder / "icons"
    if icons.is_dir():
        for f in icons.iterdir():
            if f.is_file() and f.suffix.lower() == ".png":
                n += f.stat().st_size
    return n


def free_bytes(df):
    """Available bytes from `df -k`, or None when the answer cannot be read."""
    lines = [line for line in df.splitlines() if line.strip()]
    if len(lines) < 2:
        return None
    fields = lines[-1].split()
    try:
        return int(fields[3]) * 1024
    except (IndexError, ValueError):
        return None


def check(folder, slug, font, text):
    query, _, df = text.partition("--df--")
    themes = [t for t in rows(query) if t["id"] not in ("legacy", "builtin")]
    size = theme_size(folder, font)
    if size > MAX_THEME:
        return f"the theme is {mb(size)}, over the {mb(MAX_THEME)} a slot may hold."
    others = [t for t in themes if t["id"] != slug]
    if len(others) >= MAX_SLOTS:
        return (f"the TV already keeps {len(others)} slots, the most it may hold; remove one with"
                f" tools/theme.sh --delete <slug> first.")
    total = sum(t["bytes"] for t in others) + size
    if total > MAX_TOTAL:
        return f"all slots would take {mb(total)}, over {mb(MAX_TOTAL)}; remove one with --delete first."
    free = free_bytes(df)
    if free is not None and free < 2 * size:
        return f"the TV has {mb(free)} free, less than twice the theme ({mb(size)})."
    return None


def main(argv):
    if not argv:
        print(__doc__, file=sys.stderr)
        return 2
    cmd = argv[0]
    if cmd == "size":
        print(theme_size(argv[1], argv[2] if len(argv) > 2 else ""))
        return 0
    text = sys.stdin.read()
    if cmd == "list":
        themes = rows(text)
        if not themes:
            print("No themes on the TV (or a launcher from before theme slots). Home uses the built-in config.xml"
                  " or the theme pushed without --slot.")
            return 0
        for t in themes:
            kind = "animated" if t["animated"] else "static"
            flags = ("  <- in use" if t["active"] else "") + ("" if t["usable"] else "  (broken: config.json does not parse)")
            size = "" if t["id"] == "builtin" else f"{mb(t['bytes']):>9}  {kind:<8}"
            print(f"{t['id']:<32}  {t['name']:<40}  {size}{flags}".rstrip())
        return 0
    if cmd == "use":
        wanted = argv[1]
        if wanted == "builtin":
            return 0
        found = [t for t in rows(text) if t["id"] == wanted]
        if not found:
            where = "theme pushed without --slot" if wanted == "legacy" else f"slot {wanted}"
            print(f"the TV has no {where} (tools/theme.sh --list shows what it has).")
            return 1
        if not found[0]["usable"]:
            print(f"{wanted}'s config.json does not parse on the TV, so Home would fall back; push it again.")
            return 1
        return 0
    if cmd == "delete":
        found = [t for t in rows(text) if t["id"] == argv[1]]
        if not found:
            print(f"the TV has no slot {argv[1]} (tools/theme.sh --list shows what it has).")
            return 1
        if found[0]["active"]:
            print(f"{argv[1]} is the theme in use; switch with tools/theme.sh --use <slug|legacy|builtin> first.")
            return 1
        return 0
    if cmd == "check":
        problem = check(argv[1], argv[2], argv[3] if len(argv) > 3 else "", text)
        if problem:
            print(problem)
            return 1
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
