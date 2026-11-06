# Theme customization plan (personal fork)

Work on `feat/theme-customization`, for the TCL T6C in
[`examples/tcl-t6c-indonesia/`](../examples/tcl-t6c-indonesia/). Every new key is off when a theme does not
set it, so Home without the key looks exactly as before. Each phase is measured on the TV against
[`docs/baseline/2026-11-07.md`](baseline/2026-11-07.md); PSS may rise by at most 8 MB.

| Phase | What | State |
|---|---|---|
| 0 | APK size gate 640 KB for this fork, performance baseline | done |
| 1a | Theme font: `font` (a `.ttf`/`.otf` in the theme's `fonts/`) and `font_scale` (0.85-1.25) | in progress |
| 1b | Custom icon or banner per app from the theme folder | not started |
| 2 | Tile size, gaps and label size as theme keys | not started |
| 3 | Theme picker on the TV, with the remote | not started |
| 4 | Animated wallpaper (GIF/animated WebP), if memory allows; no video | not started |

## Rules every phase keeps

- **Phase 3 must `recreate()`.** `font_scale` and `locale` are applied in each activity's
  `attachBaseContext` (`LauncherLocale.wrap`), and the theme font is loaded and its `LayoutInflater` factory
  installed in `onCreate` before `setContentView`. All of these are read once, when the activity is created.
  A theme chosen on the TV has to `recreate()` Home (and any other open launcher activity) after it writes the
  new theme, or the old font, scale and language stay on screen until Home restarts.
- Fonts and pictures live in the theme folder and are pushed by `tools/theme.sh`; nothing is bundled in the APK.
- No network, no background service, no new dependency.
