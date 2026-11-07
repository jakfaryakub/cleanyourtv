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
| 1b.5 | Minimal header top right (`header_position`, `clock_size`, `top_button_size`, `header_shadow`) with today's five prayer times under it (`prayer_times`), computed offline | done |
| 1b.3 | App row hidden while idle, shown on the first key press: `rows_autohide_seconds` | done |
| 1b.6 | Polish: `row_wrap`, `down_opens_library`, Left from the App library pill, logos for system apps (`tools/logo.py --icon`) | done |
| 2 | Tile size, gaps and label size as theme keys | parked: `tile_style` logo and `focus_scale` covered the need |
| 3 | Theme picker on the TV, with the remote | next (remember `recreate()`, below) |
| 4a | Animated WebP wallpaper (`AnimatedImageDrawable`), key `wallpaper_animated`; measure first | done: 640×360 at 12 fps passes; idle CPU 20%, pause-when-idle proposed (4a.1) |
| 4a.1 | Pause the animation after N seconds with no key: `wallpaper_pause_seconds` | done; the experiment theme uses 300 |
| 4b | MP4 wallpaper, only if 4a fails and only in this fork (breaks the repo's "no video" rule) | not needed for now: 4a passes at 640×360 |

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

## Phase 1b.6 result

Navigation and logos, checked on the T6C (Android 12, `STT2.230203.001`). No A/B: the keys change where focus goes,
not what Home draws.

- Existing keys first: `show_library_pill: false` alone did not hide the pill, as it only hides it while
  `library_button` is on. The theme had it set and the pill still sat at the row's end.
- `row_wrap` (off by default): Left from the first tile goes to the last and Right from the last to the first,
  through `focusTile`'s smooth scroll; in a grid each row wraps within itself. `GridNavigation.wrapTarget`, unit
  tested. On the TV: Left from YouTube to Weather and Right back, the other tiles still dimmed.
- `down_opens_library` (off by default, needs `app_library`): Down from the last or only row opens the App library,
  and Back returns to the tile it came from (Vidio on the TV). It counts as a way in, so
  `show_library_pill: false` now hides the pill. A shown pill still takes Down first. `GridNavigation.down`, unit
  tested.
- With `rows_autohide_seconds`, the first key, Down included, only brings the row back. `RowsAutohide` already
  did this; a test now covers Down. On the TV, a key after 30 s idle only showed the row.
- **Bug, Left from the App library pill went up.** In a single row the pill sits after the last tile, but its key
  listener was written for the footer under a grid: Left did nothing and Up went back to the row. Now the key back
  towards the row (Left, or Right in an RTL grid) goes to the last tile and Up goes to the top bar. On the TV, with
  the pill shown: Left went to Weather and Up to the Wi-Fi button. The new layout has no pill at all.
- `tools/logo.py --icon <symbol> --text "<Name>"`: a white Material Symbol (`tools/material-symbols/`, Apache 2.0,
  with source commit and licence) and the name in the theme font, through a private fontconfig. A long name is
  set smaller first, so the icon stays 120 px high. The four logos are in `work/theme-ref/icons/`: Media Player
  `com.tcl.ui_mediaCenter`, Settings `com.android.tv.settings`, TV `com.tcl.tv`, Weather
  `io.github.sumyabazar1.weather`. Play Store has no logo yet and shows its name.
- The theme turns on `row_wrap` and `down_opens_library`. APK 598,245 B, lint 0 errors.

## Phase 1b done

Phase 1b is finished: logos from the theme (1b.1), logo tiles with focus by scale and opacity (1b.2), the
minimal header with prayer times (1b.5), the app row hidden while idle (1b.3) and the 1b.6 polish. Left: 4a (animated wallpaper,
measure first) and 3 (theme picker on the TV).

`header_shadow` puts a soft shadow under the clock, the date and the prayer line. `rows_autohide_seconds` (30 in the
experiment theme) fades the app row out after that many idle seconds; the first key, or Home, only brings it back
on the same tile (`RowsAutohide`, unit tested), and it never hides while tiles move or a menu or dialog is open.

A/B/A after a clean boot, the experiment theme without (A) and with (B) `header_shadow` and `rows_autohide_seconds`:

| | A | B | A again |
|---|---|---|---|
| Uptime | 6 min | 10 min | 14 min |
| MemAvailable / SwapFree | 967,828 / 368,636 kB | 951,960 / 370,940 kB | 958,280 / 371,196 kB |
| PSS after warm-up | 47,188 KB | 46,955 KB | 46,408 KB |
| PSS after keys | 36,341 KB | 36,023 KB | 35,522 KB |
| Frame p50 / p90 | 7 / 7 ms | 7 / 7 ms | 7 / 7 ms |
| GPU p50 / p90 | 5 / 6 ms | 5 / 6 ms | 5 / 6 ms |
| Janky frames | 0.00% | 0.00% | 0.24% |

Neither key costs memory or frame time that the runs can tell apart.

## Phase 1b.5 result

Kemenag's official table for Kota Jakarta (SIHAT, read 2026-11-07, 6 10 12.32 S, 106 49 51.78 E) is in
`launcher/app/src/test/resources/prayer/`: a tuning set of 6 dates, a held-out test set of 6, and every day of the nine
months that were loaded (272 days). The parameters were confirmed on the tuning set only (0 minutes off) and the
test set was run once afterwards (at most 1 minute off).

The safety rule is asymmetric: never earlier than Kemenag, at most a minute later. Before the rule, against the
272 days:

| | Earlier | Same | 1 min later |
|---|---|---|---|
| Fajr | 0 | 267 | 5 |
| Dhuhr | 1 | 267 | 4 |
| Asr | 22 | 220 | 30 |
| Maghrib | 2 | 264 | 6 |
| Isha | 2 | 267 | 3 |

The unrounded times sat 1.39 minutes early to 0.18 late, so any margin from 24 to 49 seconds keeps every day at 0 or
+1. `Prayer.SAFETY_SECONDS` is 30; the astronomy is unchanged. After it:

| | Earlier | Same | 1 min later |
|---|---|---|---|
| Fajr | 0 | 137 | 135 |
| Dhuhr | 0 | 132 | 140 |
| Asr | 0 | 138 | 134 |
| Maghrib | 0 | 141 | 131 |
| Isha | 0 | 128 | 144 |

Names are in English (Fajr, Dhuhr, Asr, Maghrib, Isha). After Isha the line shows tomorrow's times, with Fajr
in bold (a small "Tomorrow" label was tried and dropped). The date at the top right lost both ends after a cold boot (its lines kept `match_parent` in a column
that wraps); fixed, and the longest English date, "WEDNESDAY 30 SEPTEMBER", needs about 356 of the 1,151 px it has
at `clock_size` 40.

A/B/A after a clean boot, the experiment theme without (A) and with (B) `header_position`, `clock_size` and
`prayer_times`:

| | A | B | A again |
|---|---|---|---|
| Uptime | 22 min | 26 min | 30 min |
| MemAvailable / SwapFree | 952,948 / 377,596 kB | 970,864 / 379,900 kB | 978,788 / 382,716 kB |
| PSS after warm-up | 54,445 KB | 54,653 KB | 54,145 KB |
| PSS after keys | 37,907 KB | 37,647 KB | 37,405 KB |
| Frame p50 / p90 | 7 / 7 ms | 7 / 7 ms | 7 / 7 ms |
| GPU p50 / p90 | 5 / 6 ms | 5 / 6 ms | 5 / 6 ms |
| Janky frames | 0.00% | 0.00% | 0.00% |

The header costs no frame time and well under a megabyte.

A pure holdout, Kemenag's May, July and October 2026 (93 days), was loaded after the parameters and the margin were
fixed and checked once: no time earlier, and same / +1 minute for Fajr 36 / 57, Dhuhr 60 / 33, Asr 44 / 49,
Maghrib 51 / 42, Isha 36 / 57. It is a golden test now.

Variant A was chosen: a large clock (`clock_size` 64) with the date under it (`date_below_clock`), round buttons at
34 dp (`top_button_size`), and the prayer line at least the date's size with wider gaps.

**Verified after a cold boot (unplugged, 2026-11-07 21:07, uptime 0 min):** the date at the top right whole,
"SATURDAY 7 NOVEMBER" with nothing cut at either end (`uiautomator`: 397 px wide, ending at x 1600). The top right reads as crowded: three layouts are drawn
as mock-ups for a choice (A: date under a large clock, B: prayers top left, C: prayers above the app row).

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

## Phase 4a.1 result

**The screensaver, read first (read-only, T6C).** `screensaver_enabled` 1, `screensaver_activate_on_sleep` 1,
component `com.gathin.tvhome/.ClockDream`, `screen_off_timeout` 600,000 ms, `sleep_timeout` 1,139,000 ms,
`attentive_timeout` -1, stay-on off. So the launcher's clock dream starts after **10 minutes** without input,
and the TV sleeps after about 19. The theme's own `home_idle_clock_minutes` is 0 (off).

- The dream was started once with `Somnambulator`. Home's activity was then `STOPPED`, not visible, so `onStop`
  already pauses the animation after 10 minutes.
- The launcher process still used about 20% CPU while dreaming. That is `ClockDream`, which runs in the same
  process, not the wallpaper.
- So a 300 s pause still saves 5 of those 10 minutes of animation, and the experiment theme uses 300.

**`wallpaper_pause_seconds`** (0 never, by default; 1-29 count as 30, more than 3600 as 3600). After that many
seconds with no key, `WallpaperPlayer.setPaused(true)` holds the animation on its frame. Any key, or Home, plays it
again, before `RowsAutohide` sees the key: the key is not taken, so on a hidden row it still only brings the row back.
`onStart` resets the wait. `WallpaperPause` holds the timer, unit tested. APK 600,801 B, lint 0 errors.

On the TV, with 30 s for the test:

| | Launcher CPU, `top` 10 s |
|---|---|
| Animation playing | 19.8% |
| After the pause (log: `animation paused`) | 0.1% |
| After one key | 19.8% |

Two screenshots 2 s apart during the pause were identical, and after the key they differed again.

**Clock over the bright eye, `wallpaper_dim`** with `header_shadow` on: screenshots at 0, 15 and 25
(`shots/4a1-dim*.png`, not committed). The colour filter works on the animation. Mean brightness of the wallpaper
band: 129 at 0, 110 at 15, 97 at 25. In the header the clock gains a little contrast at 15 and more at 25; the
owner picks. The theme stays at 0 until then.

## Phase 4a part 2 result

A real clip, a private file kept in `work/wallpaper-src/` and never committed: 1920×1080, 60 fps, 15.8 s, no
watermark. It loops cleanly as a whole: first and last frame differ by 0.25 (mean, 96×54 grey) against 1.24
between neighbouring frames. But 15.8 s is over the 10 s limit. The best windows of 6-10 s:
3.00-12.73 s (9.73 s, 0.22) and 4.80-10.93 s (6.13 s, 0.32).

- At 960×540 and 15 fps this detailed clip costs about 23 KB a frame whatever the quality: 9.73 s came to 5.2 MB at
  q70 and 3.4 MB at q40, over the 3 MB the launcher accepts. So both variants use the 6.13 s window:
  - B1: 960×540, 15 fps, q50, 2,680,560 B.
  - B2: 640×360, 12 fps, q70, 1,417,980 B.
- `wallpaper.jpg` is the clip's frame at 4.80 s, 1920×1080, the animation's first frame, so the switch from the JPG
  to the animation does not show.
- The three runs turn `down_opens_library` off. `bench-home.sh` presses Down in its D-pad rounds, and with the key
  on it opened the App library and ran the rest of the keys there. `work/theme-ref` keeps the key on.
- After a clean boot (unplugged), in the order A, B1, B2, each 90 s after its push. Display mode 1 at 60 fps.
  `bench-home.sh`'s idle gate refused B1 and B2 (318% and 347% idle, against 360%), because the animation itself
  keeps the TV that busy. Both were re-run with `SKIP_GATE=1`, so B1 and B2 ran at 19 and 23 min of uptime.

| | A, JPG only | B1, 960×540 15 fps | B2, 640×360 12 fps |
|---|---|---|---|
| Uptime | 4 min | 19 min | 23 min |
| PSS after warm-up | 47,616 KB | 55,733 KB (+8.1 MB) | 50,906 KB (+3.3 MB) |
| PSS after keys | 37,791 KB | 46,692 KB (+8.9 MB) | 41,392 KB (+3.6 MB) |
| D-pad frames, janky | 442, 0.00% | 617, 0.97% | 577, 0.00% |
| D-pad frame p50 / p90 | 8 / 8 ms | 10 / 12 ms | 8 / 10 ms |
| D-pad GPU p50 / p90 | 6 / 7 ms | 5 / 7 ms | 6 / 7 ms |
| Idle 30 s: frames, janky | 37, 0.00% | 475, 0.84% | 391, 0.26% |
| Idle frame p50 / p90 | 6 / 8 ms | 12 / 13 ms | 10 / 11 ms |
| Launcher CPU, Home idle 60 s (`top`) | 0.1% | 40.3% | 20.4% |
| Whole TV idle during the status step | 376% (gate) | 316% | 344% |
| MemAvailable after | 948,524 kB | 951,720 kB | 1,004,656 kB |

- **B2 passes the main limits**: janky +0.00 pp and PSS +3.6 MB. Its idle CPU, 20.4% of one core, sits at the
  survey's 20%.
- **B1 fails PSS after the keys** (+8.9 MB over +8 MB) and uses twice B2's CPU.
- MemAvailable moves with the TV's state more than with the theme, as in the earlier phases.
- **Recommended: B2**, now in `work/theme-ref`. No FATAL in logcat. With the same 1.4 MB budget a longer loop
  fits too: the 9.73 s window at 640×360 / 12 fps came to 2.1 MB at q70.

**4a.1, proposal only:** `wallpaper_pause_seconds`, 0 (never) by default. After that many seconds with no key on
Home, `WallpaperPlayer.stop()` pauses the animation on the frame it shows; the first key, or Home coming back,
starts it again. The key does not reveal the row, so it works next to `rows_autohide_seconds`, from the same
key feed in `dispatchKeyEvent`. Expected effect, from the numbers above:
- The launcher drops from 20% to about 0.1-0.3% (A's idle, and the 0.3% measured with Settings open in part 1)
  once paused.
- With 60 s, Home left alone for 10 minutes averages about 20% × 60/600 ≈ 2% of one core, and about 0.2% over
  an hour.
- No memory change: the paused drawable keeps its buffers. Releasing them as well would save about 3 MB, but the
  first key would then wait for a decode.
- A small, unit-testable timer, like `RowsAutohide`, and two calls into `WallpaperPlayer`.

## Phase 4a part 1 result

Code, tool and a function test on the T6C (Android 12, `STT2.230203.001`), no A/B yet.

- **Different from the survey.** The key is a bool, `"wallpaper_animated": true`, and the file has a fixed name,
  `wallpaper.webp` beside `wallpaper.jpg`. ThemeProvider then needs one more name, not a pattern, and `theme.sh`
  pushes it like `wallpaper.jpg` (and deletes it on `--reset` or when a theme has none).
- `AnimatedWallpaper` holds the rules, unit tested: Android 9+, not with `reduced_motion`, at most 3 MB, a RIFF
  length that matches the file, at most 1920×1080, decoded at no more than 960×540.
- `WallpaperPlayer`: the JPG is still Home's first frame. The animation is decoded on the `decoder` thread
  (`ImageDecoder`, `MEMORY_POLICY_LOW_RAM`) and replaces it; then the JPG bitmap is released. `start()`/`stop()`
  play and pause it, `onTrimMemory` drops it and `start()` decodes it again, `wallpaper_dim` is a colour filter,
  and rotation pauses while it plays. The API 28 calls sit in a nested `@RequiresApi(28)` class. APK 600,389 B,
  lint 0 errors.
- `tools/webp_wallpaper.py` cuts a video or GIF, turns it into frames with ffmpeg and encodes them with `img2webp`,
  then checks against the limits (`--check` for any file). ffmpeg's own `libwebp_anim`, and `img2webp` without
  `-kmax`, left stale blocks on a gradient: a mean error of 15 against the source frames, 1.3 with `-kmax 5`, at no
  larger size. The tool now uses `-kmax 5`.
- On the TV, with a 4 s gradient test clip at 960×540 and 15 fps (152 KB):
  - It plays on Home: two screenshots 1 s apart differ by a mean of 35 per pixel.
  - It stops while Settings is open (launcher 0.3% CPU over 10 s) and plays again back on Home.
  - After `am send-trim-memory ... COMPLETE` with Home hidden, it plays again on return.
  - `reduced_motion`, a file cut at 20 KB and a 2560×1440 file all keep the JPG, each with one
    `TvHome.Wallpaper` log line. Before the length check, the cut file played its first frames with no warning.
  - No FATAL in logcat.
- **One quick number: launcher 30.6% of one core** (`top`, 10 s, Home idle, RES 101 MB). That is over the
  survey's 20% limit, but nothing here compares it with Home without the animation.
- Phase 1b.6 leftovers: Play Store (`shop`) and "Media" logos made with `logo.py --icon`.

**Part 2 (next):** the A/B from the survey with a real clip, after a clean boot: `bench-home.sh`, idle CPU
and frames, MemAvailable, against the same theme without `wallpaper_animated`. If idle CPU stays over 20%, try
640×360 at 12-15 fps before deciding on 4b.

## Phase 4a survey

Read-only survey on 2026-11-07, no build and no TV. Nothing is written yet.

**Where it goes in `WallpaperPlayer`.**

- `AnimatedImageDrawable` and `ImageDecoder` need API 28 and the launcher's `minSdk` is 26, so everything sits
  behind `Build.VERSION.SDK_INT >= 28`; below that the JPG stays.
- `Config` (next to `WALLPAPER_NAME`): `wallpaperAnimated`, a `File` from the key. It is null when the key is unset,
  the file is missing or empty, `wallpaper` is false, `reduced_motion` is on, or the SDK is below 28.
- Constructor: `showFirst()` still decodes the JPG on the main thread, so Home's first frame is the JPG, as
  placeholder and fallback both. Then the existing `decoder` executor runs `ImageDecoder.decodeDrawable` with a
  header listener: `setTargetSize` down to the limit, refuse anything over 1920×1080,
  `setMemorySizePolicy(MEMORY_POLICY_LOW_RAM)`. On the main thread: dropped if `closed`; a still WebP keeps the
  JPG; otherwise `setRepeatCount(REPEAT_INFINITE)`, `image.setImageDrawable`, and the JPG in `current` is
  recycled 500 ms later, as `show()` does.
- `update()` returns early while the animation is on: it replaces rotation and moods.
- Pause and resume: `start()` (Home's `onStart`) calls `anim.start()`, `stop()` (`onStop`) calls `anim.stop()`,
  `close()` stops it and drops the drawable.
- `reduced_motion`: nothing is decoded, the JPG stays. That costs no memory, unlike a paused first frame.
- `releaseForMemoryPressure()` stops it, clears the drawable and sets `anim` to null; the next `start()` shows
  the JPG through `showFirst()` and decodes the animation again.
- Fallback to the JPG on any exception or `OutOfMemoryError`, a refused size, SDK below 28 or reduced motion,
  with a `TvHome.Wallpaper` warning. A theme with no `wallpaper.jpg` shows its background colour until the
  animation is ready.
- `wallpaper_dim` cannot be painted into the frames: `anim.setColorFilter` with a `PorterDuffColorFilter` in
  `SRC_ATOP`.
- The clock screensaver (`ClockScene`) keeps the still JPG.

**Key and file limits.**

- `wallpaper_animated`: a file name in the theme folder, for example `"wallpaper.webp"`. Unset, Home is
  unchanged. No key for fps; `reduced_motion` already exists.
- The file: lossy animated WebP, at most 960×540 (bigger is scaled down while decoding, over 1920×1080 is
  refused), at most 20 fps (frames of 50 ms or longer), a loop of at most 10 s, at most 3 MB. `ImageDecoder`
  cannot cap fps or loop length, so the theme tool checks those offline with Pillow; the app enforces size and
  resolution only.
- Memory estimate: two 960×540 ARGB buffers, about 4.1 MB, less the released JPG (about 4 MB in RGB_565).

**Measurement and pass limits.** A/B/A after a clean boot, the experiment theme without (A) and with (B)
`wallpaper_animated`, each run 90 s after its push:

- `tools/bench-home.sh`: PSS after warm-up and after keys, frame and GPU p50/p90, janky frames.
- Idle CPU: 60 s on Home with no keys, `top -b -d 10 -n 6 -p <launcher pid>`, the average %CPU.
- Idle frames: reset gfxinfo, 30 s with no keys, read gfxinfo (the animation draws on its own).
- The stop works: open another app, the launcher's CPU is about 0 within 10 s; back on Home it plays again.
- MemAvailable and SwapFree before and after, as in the 1b tables.

B passes against A when: PSS rises at most 8 MB; D-pad janky frames rise at most 2 pp; frame p90 stays at or
under 16 ms and GPU p90 rises at most 3 ms; idle CPU is at most 20% of one core; idle janky frames at most 5%;
CPU 0% after the stop; MemAvailable drops at most 20 MB. If one fails, try 640×360 at 15 fps once; if that fails
too, 4a closes and 4b is decided.

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
