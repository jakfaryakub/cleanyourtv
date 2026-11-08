# TCL T6C, Indonesia (G10, Google TV, Android 12)

Device G10, product `G10_4K_GB_NF_32BIT`, firmware `STT2.230203.001 AS50`, 1.8 GB RAM. This is the same
G10 platform as [tcl-g10-google-tv](../tcl-g10-google-tv/). [debloat.txt](debloat.txt) has the list, what
it keeps and why. Replace `<tv ip>` with your TV's address.

## What was done

- **36 packages disabled**, in five batches plus the launcher handover: Samba TV ACR, TCL logkit and
  account, TCL content, ads and push messages, retail demo, hotel mode and the manual, TGuard, TalkBack,
  MagiConnect, Alexa, T-Solo, Gamebar, the soundbar updater, the TCL browser, and unwatched streaming apps.
  A second Android user (id 10) exists on this TV. The list only touches user 0.
- **Kept**:
  - the Assistant on the mic button
  - Chromecast, AirPlay and Miracast
  - the USB media player
  - Netflix, YouTube, Prime Video, Bilibili and Vidio
  - `com.tcl.ttvs`, the firmware updater, which also carries TCL analytics
  - `com.seraphic.openinet.pre` and `partnersetup`
- **Samba**: the opt-out is not reachable on this firmware. Settings > Privacy has no Samba entry, and its
  activities are not exported. With `tv.samba.ssm.ui` disabled and ttvs kept, no Samba service ran after a
  reboot.
- **Launcher**: installed with `STOCK_LAUNCHER=none` first. Then setupwraith, launcherx and dreamx were
  disabled, in that order (see the list header).
  - The screensaver is `com.gathin.tvhome/.ClockDream`.
  - The saved Wi-Fi network belongs to `android.uid.system`, so setupwraith could go.
- **Volume trade-off (choice B)**: `InputKeyService` and `HomeKeyService` are kept for the remote buttons.
  With them bound, holding Vol+/Vol- moves one step per press instead of ramping. `consume_volume_keys`
  did not change that.

## Remote buttons

| Button | Keycode | Handled by |
|---|---|---|
| Gear | 83 (`KEYCODE_NOTIFICATION`) | `settings_key_keycodes` in `launcher/app/src/main/res/values/config.xml` |
| YouTube / Netflix / Prime Video | 4065 / 4062 / 4099 | theme `app_keys` |
| TV | 4001 | theme `app_keys`, mapped to `com.tcl.tv` (it went to the disabled launcherx before) |
| Vidio | 4157 | TCL firmware, no mapping needed |
| i | `KEYCODE_INFO` | does nothing on Home, shows info in Live TV and HDMI; left as is |

## Theme

[theme/](theme/) is the glass theme merged with this TV's settings: the five-app dock, `app_keys`,
`app_library` and `tile_menu`. `wallpaper.jpg` is `themes/glass/wallpaper.jpg`.

```bash
TV_HOST=<tv ip> tools/theme.sh examples/tcl-t6c-indonesia/theme
```

- The Open Browser, Quick panel and Dashboard tiles were removed from Home with the tile menu. They come
  back from the App library: hold OK on Settings, open App library, and add them to Home.
  - Their packages (`com.seraphic.openinet.pre`, `com.tcl.suspension`, `com.tcl.dashboard`) stay enabled.
    `com.tcl.suspension` is also the Input panel.
- A personal wallpaper is pushed from a copy of the theme under `work/`, which is git-ignored, with
  `"ambient_glow": false`. That keeps the image out of the repo.

## Add-ons

Weather, Prayer Times (Jakarta, method 20: Kementerian Agama RI) and Sleep Timer are installed from
`addons/`, with their cards set to Shown in Settings > Module cards.

- **Sleep Timer**: its `SleepService` is added to `enabled_accessibility_services` next to the
  launcher's two helpers, never in place of them.
- **TclAppBoot**: on this firmware it refused the first bind, because the service was enabled before
  `APP_AUTO_START` was allowed. Clearing the list and writing the same string back bound all three.
  - To check:

    ```bash
    tools/tv.py "dumpsys accessibility | grep -o 'label=[^,]*'"
    ```

    It should show Home, Home button and Sleep Timer.

## Local changes on this TV (not part of the list)

- **`launcher/app/src/main/res/values/config.xml`**: dock, `live_tv_package` `com.tcl.tv`,
  `settings_key_keycodes` 83, `app_keys` and 5 columns.
- **`MainActivity.onBackPressed`**: Back on a tile below the dock returns focus to the last focused dock
  tile and scrolls to the top. Back on the dock or the top bar still does nothing.
  - This is a local patch. A rebuild from an unpatched checkout drops it.
  - Upstream, it would have to be an off-by-default switch.
- **`addons/prayer-times/app/src/main/res/values/config.xml`**: Jakarta coordinates and method 20.

## After a firmware update

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/check.py examples/tcl-t6c-indonesia/debloat.txt
```

## Undo

- Everything in the list:

  ```bash
  TV_HOST=<tv ip> tools/restore.sh examples/tcl-t6c-indonesia/debloat.txt com.google.android.apps.tv.launcherx
  ```

- The theme:

  ```bash
  TV_HOST=<tv ip> tools/theme.sh --reset
  ```

- Sleep Timer:

  ```bash
  TV_HOST=<tv ip> addons/sleep-timer/build-and-install.sh --uninstall
  ```

- Weather and Prayer Times:

  ```bash
  tools/tv.py "pm uninstall io.github.sumyabazar1.weather"
  tools/tv.py "pm uninstall com.majnioui.tv.prayer"
  ```

- The stock screensaver:

  ```bash
  tools/tv.py "settings delete secure screensaver_components"
  ```

## Ide berikutnya (next ideas)

- **Live widgets under the clock**, the way `show_stats` sits there: the temperature in Jakarta and the next
  prayer time.
  - The add-ons would publish their values through a `ContentProvider`, and the launcher would read them.
    It would not fetch anything itself, since it has no `INTERNET` permission.
  - This needs a launcher switch that is off by default. See `docs/workflows/launcher-switch.md` and
    `docs/modules.md`.
- **Wallpaper**: replace the 736×414 test image with a higher-resolution one, of 1920×1080 or more.

## Theme font

The theme draws Home in [Plus Jakarta Sans](theme/fonts/plus-jakarta-sans.ttf) (`"font"` in
[theme/config.json](theme/config.json)), a variable font by Tokotype that covers the four weights the launcher
uses. [Manrope](theme/fonts/manrope.ttf) and [Inter](theme/fonts/inter.ttf) sit beside it to try instead. All
three are SIL Open Font License 1.1 fonts, each with its licence next to it
([plus-jakarta-sans.OFL.txt](theme/fonts/plus-jakarta-sans.OFL.txt), [manrope.OFL.txt](theme/fonts/manrope.OFL.txt),
[inter.OFL.txt](theme/fonts/inter.OFL.txt)), taken from `google/fonts`. The files were renamed from the Google
Fonts names (`PlusJakartaSans[wght].ttf` and so on), whose brackets `tools/theme.sh` does not push.

## Performance baseline

[docs/baseline/2026-11-07.md](../../docs/baseline/2026-11-07.md) has memory, CPU and frame numbers for
Home on this TV with launcher 3.4, taken before the theme-customization work.
