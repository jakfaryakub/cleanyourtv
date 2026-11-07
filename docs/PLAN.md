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
| 1b.0 | Reference look from existing keys only, in a local theme (`work/theme-ref/`) | done |
| 1b.1 | App logo from the theme: `icons/<package>.png`, key `app_logos` | done |
| 1b.2 | Logo tiles with no card, focus by scale and opacity: `tile_style: "logo"`, `focus_frame: false`, `focus_scale`, `unfocused_opacity` | done |
| 1b.4 | Combined theme push: the repo's T6C theme plus local logos from `work/theme-ref/icons/`, no logo in Git | done (by hand, see below) |
| 1b.5 | Minimal header top right (`header_position`, `clock_size`) with today's five prayer times under it (`prayer_times`), computed offline | planned, next |
| 1b.3 | App row hidden while idle, shown on the first key press: `rows_autohide_seconds` | not started, after 1b.5 |
| 2 | Tile size, gaps and label size as theme keys | not started |
| 3 | Theme picker on the TV, with the remote | not started |
| 4a | Animated WebP wallpaper (`AnimatedImageDrawable`), key `wallpaper_animated`; measure first | not started |
| 4b | MP4 wallpaper, only if 4a fails and only in this fork (breaks the repo's "no video" rule) | not decided |

## Phase 1b.1 result

`app_logos` draws `icons/<package>.png` centred on the tile's dark card; `tools/logo.py` makes the PNGs. On the T6C
all five dock apps show their wordmark (`docs/switches/app_logos.jpg`). Checked on the TV:

- With `icons/` pushed but no `app_logos` key, Home is identical to the 1b.0 screenshot (`tools/screendiff.py`,
  clock masked).
- With three logos, the other two tiles keep their own artwork.
- A logo over 4096 px, and a PNG cut short, keep the app's artwork with a `ThemeIcons` warning in logcat. Android
  decoded the cut PNG with empty rows at first, so `ThemeIcons` now draws only a file that ends in `IEND`.
- `tools/theme.sh --reset` empties `icons/` on the TV.

A/B/A with `tools/bench-home.sh`, the same theme without and with `app_logos`, run back to back without a reboot:

| | A, no logos | B, five logos | A again |
|---|---|---|---|
| Uptime | 1:44 | 1:48 | 1:52 |
| PSS after warm-up | 55,938 KB | 54,766 KB | 55,404 KB |
| PSS after keys | 39,367 KB | 38,727 KB | 39,064 KB |
| Janky frames | 22.81% | 22.20% | 29.73% |
| Frame p50 / GPU p50 | 48 / 22 ms | 48 / 22 ms | 48 / 22 ms |

The logos add no PSS that the runs can tell apart. All three runs were in the slow-GPU state from the backlog, so
their jank is not comparable with the 14:36 baseline. After a clean boot the logos cost nothing either: 7 ms p50
and 0% janky with and without them (runs 2 and 3 in the backlog entry below).

## Phase 1b.2 result

`tile_style: "logo"` draws Home's app tiles with no card; an app without a logo shows its name as a white wordmark
in the theme font. `focus_frame: false` hides the glass (it still shows while tiles are moved), `focus_scale` sets
the zoom (1.0-1.3), and `unfocused_opacity` (0.3-1.0) dims every tile in the row that is not focused, module cards'
art included, on the art views only, with no layer. `tools/logo.py --shadow` bakes a soft shadow into the PNG in a
margin outside the logo's box and names the margin in a tEXt chunk, so the logo keeps its size. The experiment
theme uses 1.2 and 0.5, and keeps Prayer Times off Home with `hidden_apps`.

A/B/A after a clean boot, the experiment theme with `app_logos` (A) against the same with the four keys (B), each
run 90 s after its push:

| | A, cards | B, logos | A again |
|---|---|---|---|
| Uptime | 10 min | 13 min | 17 min |
| MemAvailable / SwapFree | 960,436 / 388,348 kB | 975,600 / 377,852 kB | 974,000 / 379,132 kB |
| PSS after warm-up | 49,702 KB | 47,961 KB | 48,849 KB |
| PSS after keys | 39,636 KB | 38,395 KB | 39,073 KB |
| Frame p50 / p90 | 7 / 7 ms | 7 / 7 ms | 7 / 7 ms |
| GPU p50 / p90 | 6 / 6 ms | 6 / 6 ms | 6 / 6 ms |
| Janky frames | 0.00% | 0.00% | 0.00% |

The logo row draws as fast as the cards, and its PSS is about 1 MB lower (no glass, no card fills).

## Phase 1b.5 plan: minimal header and prayer times

Done before 1b.3, because hiding the row while idle leaves the header as the only thing on the wallpaper.

**Keys** (all off or unchanged when unset):

| Key | Values | What |
|---|---|---|
| `header_position` | `start` (default) or `end` | `end` puts the clock and date top right, the Wi-Fi and Settings buttons beside them on the right edge |
| `clock_size` | 24 to 72 (sp) | The clock's text size; unset keeps 52 sp. The date follows at a fixed ratio |
| `prayer_times` | bool, `false` by default | One line of the five times (Subuh, Dzuhur, Ashar, Maghrib, Isya) under the date; the next one bold |
| `latitude`, `longitude` | degrees | The place for `prayer_times` (and, when set in a theme, for `wallpaper_moods`, which today read config.xml only) |
| `prayer_ihtiyat` | 0 to 5 minutes, default 2 | Kemenag's safety margin, added before rounding up |

**Calculation** (new `Prayer.java`, pure maths beside `Sun.java`, no network):

- The sun's declination and equation of time from Meeus' low-precision formulas, evaluated at the time of each event
  and iterated twice. `Sun.java`'s day-of-year series is 1-2 minutes off for Dzuhur and Maghrib.
- Kemenag parameters: Subuh at the sun 20° below the horizon, Isya 18°, Ashar when a shadow is its object's length
  plus the noon shadow (Shafi'i), Maghrib at −1.0° (refraction, semi-diameter and the horizon's dip), Dzuhur at the
  sun's transit plus 3 minutes, the others plus `prayer_ihtiyat`, each rounded up to the minute.
- A check in a scratch script against six dates of 2026 (1 Jan, 20 Mar, 21 Jun, 1 Aug, 23 Sep, 21 Dec) at
  −6.1754, 106.8272 matched all 30 times to the minute. The reference was api.myquran.com (`/v2/sholat/jadwal/1301/…`),
  a secondary source; the parameters were chosen from 36 combinations against it, so the official table decides.

**Golden test** (`PrayerTest`, JVM): the same six dates and five times, tolerance 1 minute, from the official Kemenag
table at https://bimasislam.kemenag.go.id/jadwalshalat (DKI Jakarta, Kota Jakarta Pusat; Bimas Islam, data from
SIHAT Kemenag RI). The page is a form, so the table has to be read from it in a browser and copied into the test with
its date of reading; the myquran numbers stand in until then. Plus edge tests: a latitude where Isya never comes
(above about 48° in June) shows no Isya instead of a wrong time, and the next prayer after Isya is tomorrow's Subuh.

**Layout** (`MainActivity`, the header's `RelativeLayout`):

- `header_position: end`: `leftBlock` aligns right, before `topButtons`; the date sits left of the clock on one
  line (as in the mock-up), and the prayer line goes under both, right-aligned. The Hebrew `rtl_menu_bar` mode
  already right-aligns the date and clock rows (`moveDateBelowClock`), which is the code to follow.
- The buttons stay on the right edge (mock-up A), drawn smaller with the header (`icon_only_buttons`). Moving them
  left (mock-up B) would put them far from the clock they belong with, and the D-pad Up path from the row lands on
  the right half of the screen either way.
- The prayer line is one `TextView` with a bold span on the next prayer, in the theme font. Refreshed from the
  existing `minuteTick` only when the minute passes a prayer or midnight, so no timer, service or alarm is added.

**Cost** (estimates, to measure): under 50 KB of memory (one TextView and one string a day); GPU work is one line of
text redrawn at most once a minute, nothing per frame. A/B with `tools/bench-home.sh` after a clean boot, the theme
without and with the three keys; limits PSS +8 MB and janky ±2 pp.

**Screenshots** for `docs/switches.md`: `header_position`, `clock_size` and `prayer_times` from the TV.

## Phase 1b.4, how the combined theme is built

`work/theme-ref/` is a copy of `examples/tcl-t6c-indonesia/theme/` (config, wallpaper, fonts) with the experiment's
keys merged into `config.json`, plus `icons/` from `tools/logo.py` and the logo sources in `logo-src/` (with
`SOURCES.md`). `work/` is ignored by git, so no logo is committed; `tools/theme.sh` sends `icons/` and ignores
`logo-src/`. Rebuild the copy when the repo theme changes.

## Phase 1b target

Home like a Projectivy-style reference: a full-screen wallpaper, one row of brand wordmark logos with no tile card,
focus by scale with no frame or glow, the row hidden while idle, and a small header top right.

What existing keys already give (survey of 2026-11-07):

| Reference point | Keys | State |
|---|---|---|
| Full-screen wallpaper | `wallpaper`, `wallpaper_dim`, `cinematic_home: false`, `ambient_glow: false`, `dock_title: false` | there (still image) |
| One row at the bottom | `single_row`, `dock_row`, `rows_below_dock: 0`, `dock_band: false` | there |
| Logos with no card | `uniform_tiles` hides the label but keeps a tinted card; per-app art only from APK resources | missing |
| Focus by scale only | glow keys off; `glass_style: "rim"` still draws a 2dp outline; scale fixed at 1.10/1.12 | partly |
| Row hidden while idle | `fade_rows` only fades on scroll; `home_idle_clock_minutes` opens the clock screen | missing |
| Small header top right | `show_greeting: false`, `icon_only_buttons`, `show_inputs_pill: false`; clock stays top left at 52sp | partly |

Where the code changes go:

- 1b.1: `ThemeFiles` (an `icons/` pattern), `ThemeProvider.delete`, `tools/theme.sh` and `theme.ps1` (push and
  clear `icons/`, like `fonts/`), a new `ThemeIcons` loader modelled on `ThemeFont` (cache on
  path:length:lastModified, `inSampleSize` to the tile size, ARGB_8888 for alpha), called first in `AppAdapter.reload()`.
- 1b.2: card background `null`, label gone, banner `fitCenter` and no outline clip in `AppAdapter` bind; the
  `GlassView` kept hidden in `MainActivity.onTileFocused`; `focusScale()` reads `focus_scale`.
- 1b.3: the idle timer that `onUserInteraction` already restarts; the first key only shows the row.
- Logos are trademarks: they stay in the local theme (`work/`, ignored by git), never in the repo or the APK.
  Convert each SVG to a transparent PNG about 120 px high and at most 360 px wide.
- 1b.4: `tools/theme.sh` pushes one folder, so the combined theme is built in `work/theme-ref/`: a copy of
  `examples/tcl-t6c-indonesia/theme/` (config, wallpaper, fonts), the experiment's keys merged into its
  `config.json`, and the local `icons/`. Rebuild the copy whenever the repo theme changes; `theme.sh` sends `icons/`
  once 1b.1 adds it.

## Phase 4 measurements to start from

The T6C has 4 Cortex-A55 cores at up to 1.07 GHz and 1.8 GB of RAM. It has hardware decoders for AVC, HEVC, VP9 and
AV1 (`OMX.realtek.video.decoder`). An animated WebP is decoded in software on the CPU; a 1080p frame in ARGB costs
about 8.3 MB, so two frames are about 16.6 MB, more than the +8 MB budget. Start with 960×540 at 15-20 fps (about
4 MB), stop it in `onPause`, and measure PSS, CPU (`top`), janky frames and GPU p90 before setting the limit.
MP4 through a hardware decoder costs little CPU but breaks the "no video" rule in `AGENTS.md`, `CONTRIBUTING.md` and
`docs/launcher.md`, holds a decoder the streaming apps need, and a `TextureView` adds GPU work on every frame.

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
  - It came back after only 1 h 44 min on 2026-11-07 (Phase 1b.1 A/B): p50 48 ms, GPU 22 ms, MemAvailable
    657 MB, SwapFree 202 MB of 665 MB, load average 25. Uptime alone does not explain it.
  - **The TV's state, not the theme.** After a clean boot (unplugged, ADB came back on its own), four
    `tools/bench-home.sh` runs in a row with no reboot between, each 90 s after its theme push:

    | Run | Theme | Uptime | MemFree | MemAvailable | SwapFree | Frame p50 | GPU p50 | Janky | PSS after warm-up |
    |---|---|---|---|---|---|---|---|---|---|
    | 1 | T6C (`examples/tcl-t6c-indonesia/theme`) | 6 min | 56,692 kB | 948,216 kB | 371,964 kB | 12 ms | 7 ms | 0.28% | 50,526 KB |
    | 2 | Experiment, no `app_logos` | 10 min | 29,828 kB | 926,684 kB | 376,828 kB | 7 ms | 6 ms | 0.00% | 50,166 KB |
    | 3 | Experiment, `app_logos` | 13 min | 75,116 kB | 955,416 kB | 371,964 kB | 7 ms | 6 ms | 0.00% | 49,988 KB |
    | 4 | T6C again | 17 min | 39,768 kB | 968,188 kB | 341,500 kB | 10 ms | 7 ms | 0.09% | 52,070 KB |

    Display mode 1 at 60 fps in every run. All four are fast, so the 22 ms GPU of the 1b.1 A/B came from the TV's
    state, not from the experiment theme, which is in fact the lightest of the two (no cinematic shelf, no glass
    dock). Load average was about 25 in the fast runs too, so it is no sign of the slow state; MemAvailable (657 MB
    slow, 927-968 MB fast) and SwapFree (202 MB slow, 342-377 MB fast) are the readings that differed.
  - Next time it is slow: record MemAvailable and SwapFree before and after a clean boot, and the GPU frequency, to
    see whether memory pressure leads it.

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
