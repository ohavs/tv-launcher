# PROGRESS

## Device
- Google TV Streamer (kirkwood), Android 14, 4 GB RAM. IP `10.0.0.43:5555` (found via `adb mdns services`).
- ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. PC authorized on TV.

## Debloat (done 2026-10-24)
- 11 SAFE packages in `debloat.txt` disabled with `pm disable-user --user 0` (never uninstall).
- User keeps: Cast (mediashell), Ambient/Google Home panel (dreamx), Assistant (katniss).
- Local device dumps in `backup/` (gitignored: contains device identifiers).

## TV Home launcher (in progress)
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

## Next
- First build, install on TV, screenshot-verify, push → CI release.

## Decisions
- Inspiration: github.com/mihirgupta0900/tv-home.
- Wikimedia requires a descriptive User-Agent (robot policy) — app sends `TVHomeLauncher/1.0 (repo URL)`.
