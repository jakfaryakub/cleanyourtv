# Switches on a TV

Every new key a theme can set (the Keys table in [`themes/README.md`](../themes/README.md)) gets a
screenshot of what it does on a real TV, and this table is where they are listed: the repo and
[cleanyour.tv/addons#switches](https://cleanyour.tv/addons#switches) both show them from here. Most
keys from before this rule still have none; a screenshot for one of them is welcome.

## Adding a key

A new key needs a screenshot. Put it in `docs/switches/<key>.jpg` (an existing screenshot in the
repo that shows the key at work also counts) and add a row below:

- From a real TV, with the key set, as a JPEG or PNG, 16:9 and no wider than 1920 px (1280×720 is
  plenty), under 300 KB.
- Show where the key changes something you can see. For a key that maps a remote button, show the
  screen the button opens; for one that changes behaviour only, show the setting's effect, such as
  the panel, the menu or the app that comes up.
- Blur anything personal: Wi-Fi names, people, addresses.

`tests/test_switches.py` checks that every new key has a row here with an image that exists and
fits the rules. A row may show more than one picture - `vpn_pill` carries the pill up and the pill at
rest - and every picture in a row is held to those rules, not only the last one. Keys from before the
rule sit on its wanted list until a screenshot arrives.

## Screenshots

| Key | Screenshot |
|---|---|
| `columns` | <img src="../themes/six-columns/screenshot.jpg" width="320" alt="columns on a TV"> |
| `glass_style` | <img src="switches/glass_style.jpg" width="320" alt="glass_style halo on a TV"> |
| `sky_intensity` | <img src="sky-sunset.jpg" width="320" alt="sky_intensity on a TV"> |
| `wallpaper_rotation_minutes` | <img src="../themes/scenes/screenshot.jpg" width="320" alt="wallpaper_rotation_minutes on a TV"> |
| `festival` | <img src="../themes/dussehra/screenshot.jpg" width="320" alt="festival on a TV"> |
| `seasonal_skin` | <img src="../themes/halloween/screenshot.jpg" width="320" alt="seasonal_skin on a TV"> |
| `settings_key_panel` | <img src="quick-settings-panel.jpg" width="320" alt="settings_key_panel on a TV"> |
| `home_idle_clock_minutes` | <img src="clock-screensaver.png" width="320" alt="home_idle_clock_minutes on a TV"> |
| `back_opens_clock` | <img src="switches/back_opens_clock.jpg" width="320" alt="back_opens_clock on a TV"> |
| `gamepad_passthrough` | <img src="switches/gamepad_passthrough.jpg" width="320" alt="gamepad_passthrough on a TV"> |
| `home_ignore_channel_keys` | <img src="switches/home_ignore_channel_keys.png" width="320" alt="home_ignore_channel_keys on a TV"> |
| `consume_volume_keys` | <img src="switches/consume_volume_keys.jpg" width="320" alt="consume_volume_keys on a TV"> |
| `wifi_status_warnings` | <img src="switches/wifi_status_warnings.jpg" width="320" alt="wifi_status_warnings on a TV"> |
| `top_bar_apps` | <img src="switches/top_bar_apps.jpg" width="320" alt="top_bar_apps on a TV"> |
| `show_greeting` | <img src="switches/show_greeting.jpg" width="320" alt="show_greeting on a TV"> |
| `header_white` | <img src="switches/header_white.jpg" width="320" alt="header_white on a TV"> |
| `clock_wallpaper` | <img src="switches/clock_wallpaper.jpg" width="320" alt="clock_wallpaper on a TV"> |
| `clock_wallpaper_dim` | <img src="switches/clock_wallpaper.jpg" width="320" alt="clock_wallpaper_dim on a TV"> |
| `clock_glow` | <img src="switches/clock_glow.jpg" width="320" alt="clock_glow on a TV"> |
| `open_on_start_choice` | <img src="switches/open_on_start_choice.jpg" width="320" alt="open_on_start_choice on a TV"> |
| `open_on_start_network_wait` | <img src="switches/open_on_start_network_wait.jpg" width="320" alt="open_on_start_network_wait on a TV"> |
| `open_on_start_on_wake` | <img src="switches/open_on_start_on_wake.jpg" width="320" alt="open_on_start_on_wake on a TV"> |
| `single_row` | <img src="switches/single_row.jpg" width="320" alt="single_row on a TV"> |
| `dock_band_all` | <img src="switches/single_row.jpg" width="320" alt="dock_band_all on a TV"> |
| `uniform_tiles` | <img src="switches/uniform_tiles.jpg" width="320" alt="uniform_tiles on a TV"> |
| `lunar_date` | <img src="switches/lunar_date.jpg" width="320" alt="lunar_date on a TV"> |
| `dock_band` | <img src="switches/dock_band.jpg" width="320" alt="dock_band on a TV"> |
| `show_library_pill` | <img src="switches/show_library_pill.jpg" width="320" alt="show_library_pill on a TV"> |
| `library_ok_opens` | <img src="switches/library_ok_opens.jpg" width="320" alt="library_ok_opens on a TV"> |
| `library_excluded_apps` | <img src="switches/library_excluded_apps.png" width="320" alt="library_excluded_apps on a TV: a CANDY Android TV DVB2K's App library catalogue, factory-menu packages excluded"> |
| `date_below_clock` | <img src="switches/date_below_clock.jpg" width="320" alt="date_below_clock on a TV"> |
| `locale` | <img src="switches/locale.jpg" width="320" alt="locale set to en on a TV whose system language is Vietnamese"> |
| `power_on_home` | <img src="switches/power_on_home.jpg" width="320" alt="power_on_home on a TV"> |
| `home_long_press_home` | <img src="switches/home_long_press_home.jpg" width="320" alt="home_long_press_home on a TV"> |
| `vpn_pill` | <img src="switches/vpn_pill.jpg" width="320" alt="vpn_pill on a TV"> <img src="switches/vpn_pill_rest.jpg" width="320" alt="vpn_pill at rest: the pill outlined in red, focus on a tile"> |
| `icon_only_vpn` | <img src="switches/icon_only_vpn.jpg" width="320" alt="icon_only_vpn with no VPN up: an open lock on the plain round button, between Wi-Fi and Settings"> <img src="switches/icon_only_vpn_on.jpg" width="320" alt="icon_only_vpn with a VPN up: the closed lock in a green outline, focus on a tile"> |
| `power_on_home_always` | <img src="switches/power_on_home_always.jpg" width="320" alt="power_on_home_always on a TV"> |
| `inputs_connected_only` | <img src="switches/inputs_connected_only.jpg" width="320" alt="inputs_connected_only on a TV"> |
| `inputs_cec_state_only` | <img src="switches/inputs_cec_state_only.jpg" width="320" alt="inputs_cec_state_only: Steam Deck · HDMI 1 Connected, the bare HDMI 2 with no state, on a TV"> |
| `icon_tiles` | <img src="switches/icon_tiles.jpg" width="320" alt="icon_tiles: an app drawn from its icon in the grid on a TV"> |
| `game_day` | <img src="switches/game_day.jpg" width="320" alt="game_day: Minnesota Vikings at Detroit Lions, Kickoff in 5d 13h, with four dots, under the clock on a TV"> |
| `clock_format` | <img src="switches/clock_format.jpg" width="320" alt="clock_format on a TV"> |
| `glow_snap` | <img src="switches/glow_snap.jpg" width="320" alt="glow_snap on a TV: the glow in Netflix red behind the dock"> |
| `font` | <img src="switches/font.jpg" width="320" alt="font on a TV: Home in Plus Jakarta Sans, the clock, date, app name and top bar"> |
| `font_scale` | <img src="switches/font_scale.jpg" width="320" alt="font_scale 1.25 on a TV: larger clock, date and tile name, the long name ending in an ellipsis"> |
| `app_logos` | <img src="switches/app_logos.jpg" width="320" alt="app_logos on a TV: the YouTube, Netflix, Vidio, Prime Video and bilibili wordmarks centred on dark tiles in one row"> |
| `tile_style` | <img src="switches/tile_style.jpg" width="320" alt="tile_style logo on a TV: the YouTube, Netflix, Vidio, Prime Video and bilibili logos in one row with no cards, YouTube focused and larger"> <img src="switches/tile_style_wordmark.jpg" width="320" alt="tile_style logo on a TV: Media Player and Play Store as white wordmarks, Play Store focused, after a dimmed module card"> |
| `focus_frame` | <img src="switches/tile_style.jpg" width="320" alt="focus_frame false on a TV: the focused YouTube logo marked by its zoom alone, with no highlight around it"> |
| `focus_scale` | <img src="switches/tile_style.jpg" width="320" alt="focus_scale 1.2 on a TV: the focused YouTube logo a fifth larger than the others"> |
| `unfocused_opacity` | <img src="switches/unfocused_opacity.jpg" width="320" alt="unfocused_opacity 0.5 with animations false on a TV: Netflix focused at full opacity, the other logos at half"> |
| `header_position` | <img src="switches/header_position.jpg" width="320" alt="header_position end with date_below_clock on a TV: a large clock top right with the date under it, beside two small buttons"> |
| `clock_size` | <img src="switches/header_position.jpg" width="320" alt="clock_size 64 on a TV: the clock top right larger than the default 52, the date under it at its usual size"> |
| `prayer_times` | <img src="switches/header_position.jpg" width="320" alt="prayer_times on a TV after Isha: Tomorrow, then Fajr 04:07 in bold, Dhuhr 11:40, Asr 14:59, Maghrib 17:51 and Isha 19:03 under the date"> |
| `prayer_latitude` | <img src="switches/header_position.jpg" width="320" alt="prayer_latitude -6.1701 on a TV: the prayer times for Jakarta under the clock"> |
| `prayer_longitude` | <img src="switches/header_position.jpg" width="320" alt="prayer_longitude 106.8311 on a TV: the prayer times for Jakarta under the clock"> |
| `prayer_ihtiyat` | <img src="switches/header_position.jpg" width="320" alt="prayer_ihtiyat 2 on a TV: the prayer times under the clock with Kemenag's two minutes added"> |
| `top_button_size` | <img src="switches/header_position.jpg" width="320" alt="top_button_size 34 on a TV: the Wi-Fi and Settings buttons smaller beside the clock"> |
