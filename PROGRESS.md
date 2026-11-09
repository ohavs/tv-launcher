# PROGRESS

## Now
- Live: **TV Home v1.0.18** on the TV (Google TV Streamer, `10.0.0.43:5555`). Repo https://github.com/ohavs/tv-launcher (public).
- **Next:** verify CI release v1.0.19 + "בדוק עדכונים" on TV + screenshot of the camera swap (backup/motion/row.png) → then ROADMAP 10 → G (Google Photos/Drive) → 9 → L3 → P (parking/cars) → phone.
- L4 confirmed by user (2026-11-07): signed in, no ads in the account.
- TV is set to: layout **Clock**, font Oswald, **our screensaver after 1 min**, photo every 1 min, cameras primary = channel 2.

## Last session (2026-11-09, evening) — L2b smart dashboard
- Google Home APIs don't work on the Streamer (`Home.Permissions.API is not available`) → devices only via phone (stage 3).
  Fallback (user's choice): Google Home card opens the built-in panel; shown when no track (no room).
- Context order: camera with motion → first slot + "תנועה" badge (cards keyed by channel); playing track → first.
- Motion = inter-frame bytes of sub streams (`MotionWatch`, no decoding, ~3% of a core for 8 channels). Verified on TV:
  hits on street cams 2/4 matched passing cars, no false hits on other channels. Toggle in Settings → cameras.
- Pushed; CI/release/update check not verified yet (usage limit).

## Session before (2026-11-09) — performance + screensaver upgrade (stage 6d)
- User: Down from the big clock felt laggy ("performance first, always"); land hid too much of the clock;
  parallax too slow (home transition + screensaver); screensaver: "now playing" from Spotify + photo interval setting.
- User choices: now playing = what plays on the TV (MediaSession, no Spotify login); per-digit cap ~25%; both parallaxes faster.
- Found (part-by-part A/B on the TV): apps page = photo layer + land layer + panel shade, each a full-screen blended pass →
  GPU 34 ms/frame. Cameras were *not* the cause (earlier hint was GPU-clock noise). Fixed: baked bitmaps + one cached
  layer while the page rests → ~14 ms (60 fps). Transition spring 110 → 300, shifts 36/96/140dp.
- Wallpapers: `tools/wallpapers/place.py` re-placed every photo (cap 25% per digit incl. screensaver motion);
  4 dropped (Flatirons, Lake Mystery, Passo Pordoi, Watzmann) → 13 photos. Before: up to 100% of a digit hidden.
- Screensaver: now-playing card (only while playing), photo every 15 s–5 min (default 1 min), 36 s drift, 20 fps (GPU 8 ms).
- Verified on TV: frame stats, clock/apps pages, screensaver started by itself with the card (test build showing a
  paused track). Not seen: the card with real playback (didn't start music on the TV).

## Session before (2026-11-07, evening) — depth clock + screensaver (stage 6c)
- Built depth wallpapers (Depth Anything V2 masks), `DepthScene.kt`, `ClockDream` (DreamService), font choice, screensaver settings.

## Session before (2026-11-07, later) — big clock + parallax (design, before L2b)
- User choices: Home opens on a full-screen clock; thin & huge; the clock shrinks into the header when scrolling to apps.
- New layout `Clock` (default; Classic/Cinema kept). Photo slides up a little (parallax) while the page scrolls.

## Session before (2026-11-07) — stage 6 (TV part)
- User choices: control center opens by long-press Netflix + dashboard button; sleep timer = full off (CEC);
  panel has now-playing, quick switches, cameras; YouTube/Netflix buttons remappable (short press).
- Built `control/` (Overlay, ControlCenter, SleepTimer), wallpaper interval, quiet mode, remote-button mapping in Settings.
- Verified on TV: panel over home and over a playing video (video keeps playing), pause/−10 s, next wallpaper,
  Netflix short/long press, Home closes it, 15-min sleep timer → device slept (`mLastSleepReason=accessibility`).
  Not seen on screen: the 1-minute notice (my check missed it). Details: DEV-NOTES "Control center".
- User: control center design + features will be revisited later.

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

## Earlier (2026-10-27, late) — YouTube start delay
- User: video click → grey screen with a black play icon, video starts later ("plays the ad behind an image").
- Found: server-side ad enforcement (googlevideo holds media ~10 s when ads were stripped) + WebView default poster.
  Fixed (v1.0.14): `isInlinePlaybackNoAd` on the player request → no ads scheduled, start ~2 s; black instead of poster.

## Session before (2026-10-28) — L4 ad-free YouTube
- Order changed by user: L4 → L3 → phone. L4 choices: remote button → ours, SponsorBlock auto-skip, keep Shorts,
  replace the official tile.
- Built `youtube/YouTubeActivity` (WebView, `:yt`), `assets/youtube/tvhome.js` (ads, DRM fix, SponsorBlock, exit),
  `SponsorBlock.kt` (hash-prefix API), settings section, tile/content/remote redirect, `tools/cdp.py`.
- Found: TV client DRM'd streams (fixed, 720p cap), YouTube/Netflix remote buttons were BAL-blocked (fixed). DEV-NOTES "YouTube".

## Earlier (2026-10-27, night) — performance
- User: "much laggier". Measured: GPU-bound frames (~5 full-screen passes, 34 ms), cameras' TextureViews redrawing
  the window ~25×/s, whole-screen recomposition per key, and sideloaded APK running uncompiled.
- Fixed (v1.0.11): cached background layer, SurfaceView cameras (kept both — no UI cost now), scoped recomposition,
  debounced content row, no text blur shadows, baseline profile + `tv-home.dm` installed by the updater.
  Result: D-pad janky frames ~45–65% → ~6%, p99 150 → ~60 ms, idle 0 frames. Details: DEV-NOTES "Performance".

## Earlier (2026-10-27, evening)
- L2 done: DVR cameras (8 ch). Dashboard: 2 live cameras + all-cameras grid + now playing; Google Home card hidden.
- New: round tile style (replaced icons/mono), cinematic layout (backdrop of focused app + centred carousel),
  Beit She'an weather. Memory fix (HARDWARE bitmaps). Found vendor HWC crash with many SurfaceViews (DEV-NOTES).

## Earlier (2026-10-27)
- Launcher redesign (Plume-inspired): dashboard (clock/date/weather, now playing, smart home), glass app row with
  brand-colour focus, content row, all-apps grid, entrance + zoom-launch animations, appearance settings.
- Researched: Brave can't show youtube.com/tv (no UA override) → own WebView wrapper planned (L4); Imilab not in
  Google Home → go2rtc (L3); DVR RTSP found at 10.0.0.14 (L2, needs user/password typed by the user).

## Earlier (2026-10-24)
- Google Home remote button fixed (v1.0.5). Wrote `docs/ROADMAP.md` + `docs/DEV-NOTES.md`.
- Lesson: `monkey` started Spotify (random media key) — never use it (see DEV-NOTES).
- 2026-10-26: Spotify auto-play/"connected" investigated — Spotify Connect + its boot receiver, not TV Home. No change (user declined).
