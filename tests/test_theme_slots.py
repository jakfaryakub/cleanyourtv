"""tools/theme.sh --slot, --use, --list and --delete keep several themes on the TV (docs/PLAN.md, Phase 3).

These run the real tools/theme.sh against the stub TV of tests/test_theme_push_ownership.py, with a `content`
stub that also answers the launcher's query of content://com.gathin.tvhome.theme/themes the way ThemeProvider
does, and a `df` stub. A slot gets the same files a plain push sends, under themes/<slug>/; theme_revision and
active_theme stay at the root; every refusal answers 2 before anything is sent; the plain push and --reset still
work and take the TV off any slot. The last checks read theme_slots.py's limits and the Windows twin.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_push_ownership as harness  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
URI = "content://com.gathin.tvhome.theme"

CONTENT = r'''#!/usr/bin/env python3
import json, os, shutil, sys
args = sys.argv[1:]
with open(os.environ["FAKE_CONTENT_LOG"], "a") as f:
    f.write(json.dumps({"content": args}) + "\n")
action, uri = args[0], args[args.index("--uri") + 1]
root = os.environ["FAKE_DIR"]
rel = uri.split("com.gathin.tvhome.theme/", 1)[1]
path = os.path.join(root, rel)

def pointer():
    p = os.path.join(root, "active_theme")
    return open(p).read().strip() if os.path.isfile(p) else None

def parse(folder):
    try:
        with open(os.path.join(folder, "config.json")) as f:
            j = json.load(f)
        return j if isinstance(j, dict) else None
    except (OSError, ValueError):
        return None

def size(folder):
    n = 0
    for dirpath, _, files in os.walk(folder):
        n += sum(os.path.getsize(os.path.join(dirpath, x)) for x in files)
    return n

if action == "query":
    slots = os.path.join(root, "themes")
    p = pointer()
    names = sorted(os.listdir(slots)) if os.path.isdir(slots) else []
    if p is None:
        active = "legacy"
    elif p == "builtin":
        active = "builtin"
    elif p in names and parse(os.path.join(slots, p)) is not None:
        active = p
    else:
        active = "legacy" if os.path.isfile(os.path.join(root, "config.json")) else "builtin"
    rows = []
    for s in names:
        j = parse(os.path.join(slots, s))
        rows.append((s, (j or {}).get("theme_name") or s, size(os.path.join(slots, s)), 0, int(j is not None)))
    if os.path.isfile(os.path.join(root, "config.json")):
        j = parse(root)
        rows.append(("legacy", (j or {}).get("theme_name") or "legacy", 10, 0, int(j is not None)))
    if active == "builtin":
        rows.append(("builtin", "Built-in", 0, 0, 1))
    if not rows:
        print("No result found.")
    for i, (s, name, n, anim, ok) in enumerate(rows):
        print(f"Row: {i} id={s}, name={name}, bytes={n}, animated={anim}, usable={ok}, active={int(s == active)}")
elif action == "write":
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sys.stdin.buffer.read())
elif action == "delete":
    parts = rel.split("/")
    if len(parts) == 2 and parts[0] == "themes":
        if pointer() != parts[1]:
            shutil.rmtree(path, ignore_errors=True)
    elif os.path.isdir(path):
        for name in os.listdir(path):
            if name.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".ttf", ".otf")):
                os.remove(os.path.join(path, name))
    elif os.path.exists(path):
        os.remove(path)
'''

DF = '#!/bin/sh\necho "Filesystem 1K-blocks Used Available Use% Mounted on"\necho "/dev/fuse 4000000 1000000 ${FAKE_DF_FREE:-3000000} 25% /storage/emulated"\n'


class ThemeSlotTest(unittest.TestCase):
    setUp_harness = harness.ThemePushTest.setUp
    run_theme = harness.ThemePushTest.run_theme

    def setUp(self):
        self.setUp_harness()
        shutil.copy2(TOOLS / "theme_slots.py", self.tools / "theme_slots.py")
        # The stub tv.py of the harness drops what the shell printed; --list and the checks read it, as from tv.py.
        tv = harness.FAKE_TV.replace('    Device().shell(" ".join(args))', '    sys.stdout.write(Device().shell(" ".join(args)))')
        self.assertNotEqual(tv, harness.FAKE_TV)
        (self.tools / "tv.py").write_text(tv)
        (self.bin / "content").write_text(CONTENT)
        (self.bin / "df").write_text(DF)
        (self.bin / "df").chmod(0o755)
        (self.theme / "wallpaper.jpg").write_bytes(b"\xff\xd8" + b"\x00" * 100)

    def slot(self, slug):
        return self.device / "themes" / slug

    def push_slot(self, slug, name=None, **env):
        if name:
            config = json.loads(self.config)
            config["theme_name"] = name
            (self.theme / "config.json").write_text(json.dumps(config))
        return self.run_theme("--slot", slug, str(self.theme), **env)

    def test_a_slot_push_writes_under_the_slot_only(self):
        result = self.push_slot("red-eyes", "Red Eyes")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue((self.slot("red-eyes") / "config.json").is_file())
        self.assertTrue((self.slot("red-eyes") / "wallpaper.jpg").is_file())
        self.assertFalse((self.device / "config.json").exists())
        self.assertFalse((self.slot("red-eyes") / "theme_revision").exists())
        self.assertTrue((self.device / "theme_revision").is_file())
        self.assertFalse((self.device / "active_theme").exists(), "a slot push must not switch")
        self.assertIn("slot red-eyes", result.stdout)

    def test_list_shows_slots_and_the_one_in_use(self):
        self.push_slot("purple-eyes", "Purple Eyes")
        self.push_slot("red-eyes", "Red Eyes")
        self.assertEqual(0, self.run_theme("--use", "red-eyes").returncode)
        result = self.run_theme("--list")
        self.assertEqual(0, result.returncode, result.stderr)
        lines = result.stdout.splitlines()
        self.assertEqual(2, len(lines), result.stdout)
        self.assertTrue(lines[0].startswith("purple-eyes"))
        self.assertIn("Purple Eyes", lines[0])
        self.assertNotIn("in use", lines[0])
        self.assertIn("in use", lines[1])

    def test_use_writes_the_pointer(self):
        self.push_slot("red-eyes")
        for value in ("red-eyes", "builtin"):
            with self.subTest(value=value):
                result = self.run_theme("--use", value)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(value, (self.device / "active_theme").read_text())

    def test_use_refuses_what_the_tv_does_not_have(self):
        for value in ("nothing-here", "legacy"):
            with self.subTest(value=value):
                result = self.run_theme("--use", value)
                self.assertEqual(2, result.returncode)
                self.assertIn("Nothing was changed", result.stderr)
                self.assertFalse((self.device / "active_theme").exists())

    def test_use_refuses_a_broken_slot(self):
        self.push_slot("red-eyes")
        (self.slot("red-eyes") / "config.json").write_text("{not json")
        result = self.run_theme("--use", "red-eyes")
        self.assertEqual(2, result.returncode)
        self.assertIn("does not parse", result.stderr)

    def test_delete_removes_a_slot_but_not_the_one_in_use(self):
        self.push_slot("purple-eyes")
        self.push_slot("red-eyes")
        self.run_theme("--use", "red-eyes")
        result = self.run_theme("--delete", "red-eyes")
        self.assertEqual(2, result.returncode)
        self.assertIn("in use", result.stderr)
        self.assertTrue(self.slot("red-eyes").is_dir())
        result = self.run_theme("--delete", "purple-eyes")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(self.slot("purple-eyes").exists())

    def test_bad_command_lines_are_refused_before_anything_is_sent(self):
        for args in (("--slot", "Red", str(self.theme)), ("--slot", "legacy", str(self.theme)),
                     ("--slot", "red-eyes"), ("--slot", "../x", str(self.theme)), ("--use",),
                     ("--use", "a/b"), ("--list", "x"), ("--delete", "builtin"), ("--delete", "a", "b"),
                     ("--slot", "red-eyes", "--reset")):
            with self.subTest(args=args):
                result = self.run_theme(*args)
                self.assertEqual(2, result.returncode, result.stdout)
                self.assertIn("Nothing was sent to the TV.", result.stderr)
                self.assertFalse(self.log.exists() and self.log.read_text().strip())

    def test_limits(self):
        (self.theme / "wallpaper.webp").write_bytes(b"\x00" * (9 * 1024 * 1024))
        result = self.push_slot("big")
        self.assertEqual(2, result.returncode)
        self.assertIn("over the 8.0 MB", result.stderr)
        self.assertFalse(self.slot("big").exists())
        (self.theme / "wallpaper.webp").write_bytes(b"\x00" * (3 * 1024 * 1024))
        result = self.push_slot("tight", FAKE_DF_FREE="4000")
        self.assertEqual(2, result.returncode)
        self.assertIn("less than twice", result.stderr)
        (self.theme / "wallpaper.webp").unlink()
        for i in range(8):
            self.assertEqual(0, self.push_slot(f"s{i}").returncode)
        result = self.push_slot("s8")
        self.assertEqual(2, result.returncode)
        self.assertIn("8 slots", result.stderr)
        self.assertEqual(0, self.push_slot("s3").returncode, "pushing an existing slot again is not a new one")

    def test_a_plain_push_and_reset_take_the_tv_off_a_slot(self):
        self.push_slot("red-eyes")
        self.run_theme("--use", "red-eyes")
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue((self.device / "config.json").is_file())
        self.assertFalse((self.device / "active_theme").exists())
        self.assertTrue(self.slot("red-eyes").is_dir(), "a plain push keeps the slots")
        self.run_theme("--use", "red-eyes")
        result = self.run_theme("--reset")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse((self.device / "active_theme").exists())
        self.assertTrue((self.slot("red-eyes") / "config.json").is_file(), "--reset keeps the slots")


class SlotLimitsTest(unittest.TestCase):
    def run_helper(self, *args, stdin=""):
        return subprocess.run([sys.executable, "-I", str(TOOLS / "theme_slots.py"), *args], input=stdin,
                              capture_output=True, text=True)

    def test_rows_with_a_comma_in_the_name(self):
        out = "Row: 0 id=a, name=Red, Eyes, bytes=5, animated=1, usable=1, active=0\n"
        result = self.run_helper("list", stdin=out)
        self.assertIn("Red, Eyes", result.stdout)
        self.assertIn("animated", result.stdout)

    def test_no_rows(self):
        result = self.run_helper("list", stdin="No result found.\n")
        self.assertEqual(0, result.returncode)
        self.assertIn("No themes on the TV", result.stdout)

    def test_the_slug_rule_matches_the_launcher(self):
        java = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/ThemeFiles.java").read_text()
        self.assertIn('Pattern.compile("[a-z0-9][a-z0-9-]{0,31}")', java)
        self.assertIn("^[a-z0-9][a-z0-9-]{0,31}$", (TOOLS / "theme.sh").read_text())
        self.assertIn("^[a-z0-9][a-z0-9-]{0,31}$", (TOOLS / "theme.ps1").read_text())

    def test_the_windows_twin_has_the_slot_forms(self):
        ps1 = (TOOLS / "theme.ps1").read_text()
        for word in ("-Slot", "-Use", "-List", "-Delete", "theme_slots.py", "active_theme"):
            self.assertIn(word, ps1)


if __name__ == "__main__":
    unittest.main()
