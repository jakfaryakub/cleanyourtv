#!/usr/bin/env bash
# Measure Home on the TV the same way every time, for an A/B of two themes or two builds.
#
# Usage: TV_HOST=192.168.1.50 tools/bench-home.sh [label]
#        SKIP_GATE=1 TV_HOST=192.168.1.50 tools/bench-home.sh nofont     # measure even on a busy TV
#
# The fixed procedure, so a number from one run can stand next to another:
#   1. Gate: the TV's CPU is at least 90% idle (360% of 400% on four cores) and at most 2% I/O wait, in two samples
#      45 seconds apart. Load average is no gate: Realtek boards keep about 22 driver threads asleep in state D,
#      which counts as a load of 22 on an idle TV.
#   2. Restart Home with theme_restart.py, which also puts the remote's key helpers back (a bare
#      `am force-stop` of the launcher switches them off).
#   3. Warm-up: 20 s on Home, open and close the App library, open and close the tile menu on the focused tile,
#      back to Home, 10 s idle.
#   4. Status: uptime, CPU, MemFree, MemAvailable, SwapFree, display mode (DisplayManager and SurfaceFlinger,
#      which number the same modes differently), the focused app, PSS and the view count.
#   5. Frames: reset gfxinfo, then 3 rounds of 14 D-pad keys 0.4 s apart (dock right and left, down into the
#      grid and back up), then gfxinfo and PSS again.
# Nothing on the TV is changed beyond what the launcher does with those keys. Read-only otherwise.
#
# Exit status: 0 measured; 1 the TV answered but a step failed; 2 nothing was measured (no TV_HOST, no venv,
# or the gate did not pass).
set -euo pipefail
cd "$(dirname "$0")"
LABEL="${1:-run}"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 [label]}"
PY=venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 2; }
PKG=com.gathin.tvhome
tv() { "$PY" -I tv.py "$@" 2>&1 | grep -v 'Broken pipe' || true; }

cpu_line() { tv "top -d 2 -n 2 -b | grep -E '^[0-9]+%cpu' | tail -1"; }
idle_ok() {   # "400%cpu ... 372%idle 0%iow ..." -> idle >= 360 and iow <= 2
  local idle iow
  idle=$(printf '%s' "$1" | grep -oE '[0-9]+%idle' | tr -dc '0-9')
  iow=$(printf '%s' "$1" | grep -oE '[0-9]+%iow' | tr -dc '0-9')
  [ -n "$idle" ] && [ "$idle" -ge 360 ] && [ "${iow:-9}" -le 2 ]
}

echo "# bench-home $LABEL $(date '+%Y-%m-%d %H:%M:%S')"
if [ "${SKIP_GATE:-}" != 1 ]; then
  first=$(cpu_line); sleep 45; second=$(cpu_line)
  echo "gate: $first"
  echo "gate: $second"
  if ! idle_ok "$first" || ! idle_ok "$second"; then
    echo "the TV is busy (needs >= 360% idle and <= 2% iow twice); nothing was measured. SKIP_GATE=1 measures anyway." >&2
    exit 2
  fi
fi

"$PY" -I theme_restart.py >/dev/null || { echo "Home did not restart" >&2; exit 1; }

STATUS="uptime; top -d 2 -n 2 -b | grep -E '^[0-9]+%cpu' | tail -1; grep -E 'MemFree|MemAvailable|SwapTotal|SwapFree' /proc/meminfo | tr -s ' '; dumpsys display | grep -m1 -oE 'mActiveModeId=[0-9]+'; dumpsys SurfaceFlinger | grep -m1 'refresh-rate'; dumpsys activity activities | grep -m1 -oE 'mFocusedApp=[^ ]+ [^ ]+ [^ ]+'"
MEM="dumpsys meminfo $PKG | grep -E 'TOTAL PSS|Java Heap:|Native Heap:|Code:|\\.ttf mmap|Views:'"
KEYS='for i in 1 2 3; do for k in RIGHT RIGHT RIGHT RIGHT LEFT LEFT LEFT LEFT DOWN RIGHT RIGHT LEFT LEFT UP; do input keyevent DPAD_$k; sleep 0.4; done; done'

echo "## warm-up"
tv "input keyevent HOME; sleep 20; am start -a $PKG.action.APP_LIBRARY -n $PKG/.MainActivity >/dev/null; sleep 3; input keyevent BACK; sleep 2; input keyevent MENU; sleep 2; input keyevent BACK; sleep 1; input keyevent HOME; sleep 10; echo done"
echo "## status"
tv "$STATUS; $MEM"
echo "## frames"
tv "dumpsys gfxinfo $PKG reset >/dev/null; $KEYS; dumpsys gfxinfo $PKG | grep -E 'Total frames|Janky frames:|50th percentile|90th percentile|99th percentile|50th gpu|90th gpu|Missed Vsync|Slow UI'"
echo "## after"
tv "$MEM; dumpsys activity activities | grep -m1 -oE 'mFocusedApp=[^ ]+ [^ ]+ [^ ]+'"
