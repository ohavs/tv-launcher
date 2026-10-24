# PROGRESS

## Device
- Google TV Streamer (kirkwood), Android 14, 4 GB RAM. IP `10.0.0.43:5555` (found via `adb mdns services`).
- ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. PC authorized on TV.

## Debloat (done 2026-10-24)
- 11 SAFE packages in `debloat.txt` disabled with `pm disable-user --user 0` (never uninstall).
- User keeps: Cast (mediashell), Ambient/Google Home panel (dreamx), Assistant (katniss).
- User apps disabled on request: iQIYI, Disney+, Apple TV (listed in `debloat.txt`). Uninstalled on request: Projectivy, Surfshark.
- Local device dumps in `backup/` (gitignored: contains device identifiers).

## TV Home launcher (live since 2026-10-24, v1.0.4 verified on TV)
- Repo: https://github.com/ohavs/tv-launcher (public). Package `com.ohav.tvhome`.
- Stack: Kotlin + Compose (no tv-material), AGP 8.13.2 / Gradle 8.14.4 / Kotlin 2.2.21.
- Features: tvOS-style focus (spring lift, entry tilt, idle sway, sheen, edge bump), 5-col grid,
  Top Shelf from TvProvider (Watch Next + preview channels, needs READ_EPG_DATA = normal perm),
  rotating wallpapers (12 bundled Commons photos, crossfade every 5 min; aerial video removed in 1.0.4 as too heavy),
  long-press menu (move with jiggle / hide / app info), Home Settings (check updates, video toggle, hidden apps, credits).
- Self-update: GitHub Releases `v1.0.<run_number>`, asset `tv-home.apk`, PackageInstaller session.
- Home button: launcherx must stay ENABLED (Google Home panel + dashboard bind to its coreservices; disabling it
  breaks Google Home with "something went wrong"). Google TV forces Home to launcherx regardless of the Home role,
  so `HomeKeyService` (accessibility, key filter) eats short Home → TV Home, long Home → dashboard
  (GLOBAL_ACTION_NOTIFICATIONS), and bounces launcherx HomeActivity if it still appears (boot).
  Component-level disable of launcherx HomeActivity is refused for shell.
- Install on TV: `install-launcher.cmd` (enables launcherx/setupwraith, enables the a11y service).
- Switch back: `launcher-stock.cmd`; full undo: `restore-all.cmd`.
- Local builds: use `BUILD_NUMBER=<next CI run>` or adb refuses the downgrade.

## Secrets (GitHub Actions)
- KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
- Local keystore: `C:\Users\ohav8\.android-keys\tv-launcher.jks` + `keystore.properties` (copied to repo root, gitignored). Same key for all versions.

## Verified on TV
- Home from inside an app → TV Home. Long-press menu, move/jiggle, Top Shelf (YouTube 17 items, Netflix Watch Next).
- In-app update 1.0.1 → 1.0.2 via Settings (system confirm dialog appears because first install was via adb).
- Memory ~120 MB PSS with wallpapers.
- 1.0.4: Home from Netflix/YouTube → TV Home, long Home → dashboard, Google Home panel opens and Back returns.

## Known limits
- After a reboot the stock home may flash for a moment before the service bounces to TV Home.
- Top Shelf needs READ_TV_LISTINGS (runtime; install script grants it, app also asks).

## Next
- Optional polish: per-app shelf tweaks, wallpaper interval setting.

## Decisions
- Inspiration: github.com/mihirgupta0900/tv-home.
- Wikimedia requires a descriptive User-Agent (robot policy) for any direct downloads.
