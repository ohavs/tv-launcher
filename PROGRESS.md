# PROGRESS

## Device
- Google TV Streamer (kirkwood), Android 14, 4 GB RAM. IP `10.0.0.43:5555` (found via `adb mdns services`).
- ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. PC authorized on TV.

## Debloat (done 2026-10-24)
- 11 SAFE packages in `debloat.txt` disabled with `pm disable-user --user 0` (never uninstall).
- User keeps: Cast (mediashell), Ambient/Google Home panel (dreamx), Assistant (katniss).
- Local device dumps in `backup/` (gitignored: contains device identifiers).

## TV Home launcher (live since 2026-10-24, v1.0.2 verified on TV)
- Repo: https://github.com/ohavs/tv-launcher (public). Package `com.ohav.tvhome`.
- Stack: Kotlin + Compose (no tv-material), Media3 ExoPlayer, AGP 8.13.2 / Gradle 8.14.4 / Kotlin 2.2.21.
- Features: tvOS-style focus (spring lift, entry tilt, idle sway, sheen, edge bump), 5-col grid,
  Top Shelf from TvProvider (Watch Next + preview channels, needs READ_EPG_DATA = normal perm),
  aerial video background (Wikimedia Commons 1080p VP9, streamed + cached 1.2 GB, muted, no audio focus),
  long-press menu (move with jiggle / hide / app info), Home Settings (check updates, video toggle, hidden apps, credits).
- Self-update: GitHub Releases `v1.0.<run_number>`, asset `tv-home.apk`, PackageInstaller session.
- Install on TV: `install-launcher.cmd` (disables launcherx + setupwraith — user approved; turns off Projectivy a11y).
- Switch back: `launcher-stock.cmd` / `launcher-projectivy.cmd`; full undo: `restore-all.cmd`.

## Secrets (GitHub Actions)
- KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
- Local keystore: `C:\Users\ohav8\.android-keys\tv-launcher.jks` + `keystore.properties` (copied to repo root, gitignored). Same key for all versions.

## Verified on TV
- Home from inside an app → TV Home. Long-press menu, move/jiggle, Top Shelf (YouTube 17 items, Netflix Watch Next).
- In-app update 1.0.1 → 1.0.2 via Settings (system confirm dialog appears because first install was via adb).
- Memory ~136 MB PSS (stock launcher was 350 MB+ across two processes).

## Known limits
- Google TV dashboard (Settings key / long-press Home in other apps) belonged to launcherx → gone while it is disabled.
  On the home screen the Settings key opens system settings; "Google Home" tile opens the smart-home panel.
- Top Shelf needs READ_TV_LISTINGS (runtime; install script grants it, app also asks).

## Next
- Optional polish: ordering of Google Home tile, more aerial clips, per-app shelf tweaks.

## Decisions
- Inspiration: github.com/mihirgupta0900/tv-home.
- Wikimedia requires a descriptive User-Agent (robot policy) — app sends `TVHomeLauncher/1.0 (repo URL)`.
