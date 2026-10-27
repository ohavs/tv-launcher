# PROGRESS

## Now
- Live: **TV Home v1.0.10** on the TV (Google TV Streamer, `10.0.0.43:5555`). Repo https://github.com/ohavs/tv-launcher (public).
- **Next: ROADMAP שלב L3** (Imilab via go2rtc) → `docs/ROADMAP.md`. Then L4 (YouTube TV). L2b (smart dashboard) optional.
- TV is set to: layout **Cinematic**, tiles **Banners**, cameras primary = channel 2.

## Done
- Debloat: 11 system packages + iQIYI/Disney+/Apple TV disabled (`debloat.txt`, `pm disable-user`); Projectivy + Surfshark uninstalled on request.
- TV Home (`com.ohav.tvhome`, Kotlin/Compose): dashboard + apps row + content row (v1.0.7), all-apps grid,
  long-press menu (move/hide/info), rotating wallpapers (12 bundled photos, 5 min), Home Settings (updates,
  Home-button service, appearance, weather city, hidden apps, credits), self-update from GitHub Releases.
- Home button + remote Google Home button handled by `HomeKeyService` (accessibility); stock launcher stays enabled.
- Scripts: `install-launcher.cmd`, `launcher-stock.cmd`, `restore-all.cmd`. Test tools: `tools/hid/`.

## Secrets (GitHub Actions) — names only
- KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
- Local keystore `C:\Users\ohav8\.android-keys\tv-launcher.jks` + `keystore.properties` (repo root, gitignored). Same key forever.

## Last session (2026-10-27, evening)
- L2 done: DVR cameras (8 ch). Dashboard: 2 live cameras + all-cameras grid + now playing; Google Home card hidden.
- New: round tile style (replaced icons/mono), cinematic layout (backdrop of focused app + centred carousel),
  Beit She'an weather. Memory fix (HARDWARE bitmaps). Found vendor HWC crash with many SurfaceViews (DEV-NOTES).

## Session before (2026-10-27)
- Launcher redesign (Plume-inspired): dashboard (clock/date/weather, now playing, smart home), glass app row with
  brand-colour focus, content row, all-apps grid, entrance + zoom-launch animations, appearance settings.
- Researched: Brave can't show youtube.com/tv (no UA override) → own WebView wrapper planned (L4); Imilab not in
  Google Home → go2rtc (L3); DVR RTSP found at 10.0.0.14 (L2, needs user/password typed by the user).

## Earlier (2026-10-24)
- Google Home remote button fixed (v1.0.5). Wrote `docs/ROADMAP.md` + `docs/DEV-NOTES.md`.
- Lesson: `monkey` started Spotify (random media key) — never use it (see DEV-NOTES).
- 2026-10-26: Spotify auto-play/"connected" investigated — Spotify Connect + its boot receiver, not TV Home. No change (user declined).
