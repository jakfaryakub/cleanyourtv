# Theme customization plan (personal fork)

Work on `feat/theme-customization`, for the TCL T6C in
[`examples/tcl-t6c-indonesia/`](../examples/tcl-t6c-indonesia/). Every new key is off when a theme does not
set it, so Home without the key looks exactly as before. Each phase is measured on the TV against
[`docs/baseline/2026-11-07-1436.md`](baseline/2026-11-07-1436.md) with `tools/bench-home.sh`; PSS may rise by at
most 8 MB, and janky frames may differ by at most 2 percentage points.

| Phase | What | State |
|---|---|---|
| 0 | APK size gate 640 KB for this fork, performance baseline | done |
| 1a | Theme font: `font` (a `.ttf`/`.otf` in the theme's `fonts/`) and `font_scale` (0.85-1.25) | done |
| 1b | Custom icon or banner per app from the theme folder | not started |
| 2 | Tile size, gaps and label size as theme keys | not started |
| 3 | Theme picker on the TV, with the remote | not started |
| 4 | Animated wallpaper (GIF/animated WebP), if memory allows; no video | not started |

## Phase 1a result

The T6C theme draws Home in Plus Jakarta Sans. In `tools/bench-home.sh`, after a clean boot, the A/B against the
same theme in Outfit gave:

| | Outfit | Plus Jakarta Sans | Difference |
|---|---|---|---|
| PSS after warm-up | 51,499 KB | 50,460 KB | −1,039 KB |
| Janky frames | 0.28% | 0.19% | −0.09 pp |
| Frame p50 | 11 ms | 11 ms | none |

- The font file costs 174 KB of PSS, and the APK grew by 2,284 B.
- `font_scale` 1.15 and 1.25 were checked in screenshots. At 1.25, long tile names end in "…".
- The clock screensaver keeps its own text sizes on purpose.

## Backlog

- **GPU slow after a long uptime.** Late on 2026-11-07, every build (the baseline APK included) drew Home at
  p50 46 ms. GPU time per frame was 22 ms instead of 7 ms; after a clean boot it was back to 11 ms.
  - Run `tools/bench-home.sh` with an uptime over 6 hours and compare it with the 14:36 baseline.
  - Record uptime, MemFree, MemAvailable, SwapFree and the GPU percentiles.
  - If it reproduces, read the GPU frequency and `dumpsys gfxinfo` framestats on both runs.

## Using the TV

If Home starts to feel heavy (slow focus moves, a stutter while scrolling), restart the TV. Power it off, unplug it
for 10 seconds and switch it on again. That cleared the slowdown above.

## Rules every phase keeps

- **Phase 3 must `recreate()`.** `font_scale` and `locale` are applied in each activity's
  `attachBaseContext` (`LauncherLocale.wrap`), and the theme font is loaded and its `LayoutInflater` factory
  installed in `onCreate` before `setContentView`. All of these are read once, when the activity is created.
  A theme chosen on the TV has to `recreate()` Home (and any other open launcher activity) after it writes the
  new theme, or the old font, scale and language stay on screen until Home restarts.
- Fonts and pictures live in the theme folder and are pushed by `tools/theme.sh`; nothing is bundled in the APK.
- No network, no background service, no new dependency.
