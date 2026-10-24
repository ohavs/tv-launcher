# PROGRESS

## Now
- Live: **TV Home v1.0.5** on the TV (Google TV Streamer, `10.0.0.43:5555`). Repo https://github.com/ohavs/tv-launcher (public).
- **Next: ROADMAP שלב 1** (monorepo + phone app skeleton) → `docs/ROADMAP.md`.

## Done
- Debloat: 11 system packages + iQIYI/Disney+/Apple TV disabled (`debloat.txt`, `pm disable-user`); Projectivy + Surfshark uninstalled on request.
- TV Home (`com.ohav.tvhome`, Kotlin/Compose): tvOS-style focus effects, 5-col grid, long-press menu (move/hide/info),
  Top Shelf (TvProvider), rotating wallpapers (12 bundled photos, 5 min), Google Home tile, Home Settings (updates,
  Home-button service state, hidden apps, credits), self-update from GitHub Releases.
- Home button + remote Google Home button handled by `HomeKeyService` (accessibility); stock launcher stays enabled.
- Scripts: `install-launcher.cmd`, `launcher-stock.cmd`, `restore-all.cmd`. Test tools: `tools/hid/`.

## Secrets (GitHub Actions) — names only
- KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
- Local keystore `C:\Users\ohav8\.android-keys\tv-launcher.jks` + `keystore.properties` (repo root, gitignored). Same key forever.

## Last session (2026-10-24)
- Google Home remote button fixed (v1.0.5). Wrote `docs/ROADMAP.md` + `docs/DEV-NOTES.md`.
- Lesson: `monkey` started Spotify (random media key) — never use it (see DEV-NOTES).
