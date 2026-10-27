# DEV-NOTES — device facts & gotchas (read when touching the related area)

## Device
- Google TV Streamer `kirkwood`, Android 14 (SDK 34), 4 GB RAM (~1.4 GB free with an app open), armeabi-v7a apps.
- ADB over network: `10.0.0.43:5555` (mDNS `_adb._tcp`). ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- Shell user has groups `input`, `uhid` — but SELinux blocks writing `/dev/input/*`.
- Open ports: 5555 adb, 6466/6467 Android TV Remote v2 (`com.google.android.tv.remote.service`), 8008/8009/8443 Cast.
- Display 1920x1080 @ 320 dpi → layouts are **960x540 dp**. UI language Hebrew → RTL (row index 0 is on the right).

## Home network
- Provision DVR (probably) `10.0.0.14`: RTSP 554 open, `Digest realm="RTSP SERVER"`, no HTTP on 80. PC is `10.0.0.24`.
- Imilab EC5 can't be linked to Google Home (user). go2rtc `xiaomi` source lists EC5 Floodlight
  (`chuangmi.camera.055a02` / `055c02`, cs2) as supported (github.com/AlexxIT/go2rtc/issues/1982); needs cloud for keys, stream is LAN.

## YouTube
- `youtube.com/tv` serves "YouTube on TV" (leanback/kabuki) when the UA is a TV/Cobalt UA.
- Ours (L4, `YouTubeActivity`, `:yt` process): WebView 155 + **Cobalt UA** (Google_ATV_kirkwood). A webOS UA gets
  "sign in to confirm you're not a bot" even as guest — keep Cobalt. Viewport 960x540 @ DPR 2 looks right.
- InnerTube responses arrive by XHR (`responseType ''`) + `JSON.parse` → the `JSON.parse` hook in
  `assets/youtube/tvhome.js` empties `adPlacements`/`adSlots` (verified: raw had 2/3, video starts with no ad).
  Calling `/youtubei/v1/player` ourselves from the page → LOGIN_REQUIRED (needs the app's attestation).
- **Many videos are Widevine-DRM'd for TV clients.** The player asks EME for `HW_SECURE_ALL`/`SW_SECURE_DECODE` and
  Cobalt-only MIME params (`enableflushduringseek=true`); WebView supports only `SW_SECURE_CRYPTO` → "This video
  format is not supported". Fix in tvhome.js: retry with plain MIME + SW_SECURE_CRYPTO and report the requested
  config back from `getConfiguration()`. Result: DRM'd videos play at **720p max** (L3); non-DRM get 1080p AV1
  (`c2.mtk.av1.decoder`), 0 dropped frames.
- Keys: BACK → we send ESCAPE (the app's back: closes panels, then "exit"); `window.close` → finish().
  `KEYCODE_MEDIA_PLAY_PAUSE` reaches the page (179). The Streamer remote has **no** play/pause key (kl file).
- Remote YouTube/Netflix buttons (BUTTON_3/BUTTON_4): launcherx `GlobalKeyListener` launches the app from the
  background → **BAL-blocked** whenever TV Home (or any app) is in front. `HomeKeyService` launches them itself.
- `:yt` ~93 MB PSS + renderer ~196 MB RSS while playing. Home → audio stops; reopening resumes playback.
- Debug: `adb shell setprop log.tag.YouTube DEBUG` (then reopen) → page console in logcat + DevTools;
  `python tools/cdp.py "<js>"` evaluates in the page (`--reload "<pre-script>" "<url>" "<js>"` for load-time hooks).
- SmartTube **27.99** is installed (`com.teamsmart.videomanager.tv`, June 2025) — older than the Dec 2025 key compromise
  (bad builds 30.43/30.47; clean builds from 30.56 have a new package). User doesn't want to sign in to it.

## Testing rules
- **Never use `monkey` to launch apps** — it injects a random event (e.g. MEDIA_PLAY → Spotify starts playing).
  Launch with: `cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER <pkg>` → `am start -n <component>`.
- `adb shell input keyevent` events are injected and **skip accessibility key filters**. To test like a real remote use
  the `hid` tool with a virtual remote: `tools/hid/make.py` (JSON → `/data/local/tmp`, `adb shell hid file.json`).
- Top activity: `dumpsys activity activities | grep -m1 topResumedActivity`.
- Screenshot: `adb exec-out screencap -p > backup/x.png` (backup/ is gitignored); downscale before viewing.
- Local builds: `BUILD_NUMBER=<next CI run number> ./gradlew :app:assembleRelease`, else adb refuses a versionCode downgrade.
  The first build after edits sometimes fails in ~4 s with no message; check for `BUILD SUCCESSFUL` and rerun,
  otherwise the old APK gets installed.
- Git Bash: prefix adb commands with `MSYS_NO_PATHCONV=1`, else `/data/local/tmp/...` is rewritten to a Windows path.
- D-pad tests: `input keyevent DPAD_*` is fine (only Home/Google Home need `tools/hid`). Contact sheets:
  Pillow is in `C:/Users/ohav8/AppData/Local/Programs/Python/Python310/python.exe`.
- `local.properties`: `sdk.dir=C:/Users/ohav8/AppData/Local/Android/Sdk` (forward slashes).

## Home button / stock launcher
- Google TV forces Home to `launcherx` regardless of the Home role (`cmd role get-role-holders android.app.role.HOME` = us).
- launcherx must stay **enabled**: Google Home panel (dreamx smarthome) binds to launcherx coreservices
  (`TrustedNetworkStateService`); disabled → "משהו השתבש". Dashboard also lives in launcherx.
- `pm disable <launcherx>/.home.HomeActivity` (component) is refused for shell.
- `setupwraith` has higher HOME priority than 3rd-party launchers only when launcherx is disabled (left enabled now).
- `HomeKeyService` (accessibility, `flagRequestFilterKeyEvents`): short Home → TV Home, long Home (600 ms) →
  `GLOBAL_ACTION_NOTIFICATIONS` (= Google TV dashboard), bounces launcherx HomeActivity if it appears (boot).
- Remote Google Home button = `KEYCODE_MACRO_1` (HID usage 0x0c0186). launcherx's FGS tries to start
  `OPEN_SMART_HOME` but Android blocks it (BAL) when launcherx home isn't visible → our a11y service starts it
  (a11y services are BAL-exempt); second press = BACK if `rootInActiveWindow` is dreamx.
- `am force-stop com.ohav.tvhome` **clears** `enabled_accessibility_services` → re-run the two `settings put` lines below.
- Accessibility enabled via adb: `settings put secure enabled_accessibility_services com.ohav.tvhome/com.ohav.tvhome.HomeKeyService`
  + `accessibility_enabled 1`. Sideloaded apps hit "restricted settings" in the UI; adb bypasses it. Survives app updates (verified).
- Remote key layout `/system/usr/keylayout/Vendor_18d1_Product_9451.kl`: MACRO_1, BUTTON_3 (YouTube key), BUTTON_4 (Netflix key).

## Launcher UI (Compose)
- On window focus Compose focuses the top-left focusable (the Settings circle) → HomeScreen re-focuses the apps row
  until "settled", then restores the last section on every window focus.
- Key handling is ours: Left/Right inside a section, Up/Down between sections (Top/Widgets/Apps/Content) with
  remembered index. Avoids Compose's geometric search jumping between rows.
- No shadow on translucent glass: the elevation shadow shows *through* the surface as a light bar.
- App icons: adaptive layers rendered at 216px, cropped to the 72/108 visible area. Glyph = foreground if it reads on
  dark (mean luminance > 0.10), else the flat plate behind the logo is keyed out (YouTube/Netflix/Cellcom). Logos are
  trimmed to their bounds and drawn at one optical size. Focus fills the tile with the icon's background colour.
- "Now playing": `MediaListener` (NotificationListenerService) only for `getActiveSessions`; grant with
  `cmd notification allow_listener com.ohav.tvhome/com.ohav.tvhome.media.MediaListener` (install script does it).
  Paused sessions older than 30 min are hidden (Spotify's boot session).
- Weather: Open-Meteo, refreshed on resume when > 30 min old; city from a fixed list (no keyboard).
- Memory: decode wallpaper/artwork as `Bitmap.Config.HARDWARE` (one GPU copy instead of heap + texture):
  home with 2 live cameras 106 MB PSS classic, ~135 MB cinematic (1280px backdrops). Budget 150 MB.
- Cinematic layout reads `lastIndex` while composing → `focusMoves` state forces recomposition on focus moves.
- ImageLoader cache key includes the requested width (thumbnails vs backdrops).

## Performance (measured 2026-10-27, `dumpsys gfxinfo` + `framestats` while pressing DPAD)
- GPU (PowerVR) is fill-rate bound: each full-screen translucent pass costs ~5 ms. Wallpaper/backdrop + scrims live in
  one `CompositingStrategy.Offscreen` layer (cached, redrawn only when the picture changes); no window background
  (`setBackgroundDrawable(null)`) and no root background. Check with `setprop debug.hwui.overdraw show` +
  `service call activity 1599295570`; set it back to `false` after.
- Blurred text shadows (`Shadow(blurRadius)`) cost ~3 ms of RenderThread per frame → none; scrims keep text legible.
- TextureView redraws the whole window on every video frame (2 cameras = ~25 redraws/s, 34% CPU idle). Dashboard
  cameras use SurfaceView (punches a hole; rounded clip works because the window around it is opaque). It can't
  fade (alpha < 1 → offscreen layer → no hole) or follow 3D rotation (`tilt = false`). 2 SurfaceViews + full-screen
  camera + grid cycled: no HWC crash.
- `HomeScreen` content is one recompose scope (Box/Column are inline): read focus state only in `derivedStateOf`
  or inside `Isolated { }`; the focused tile's zIndex comes from the tile itself.
- Sideloaded installs are only `verify` (Compose interpreted/JIT): ~2× the janky frames. `baseline-prof.txt`
  (wildcards over our code + Compose/Kotlin) → AGP writes `outputs/apk/release/baselineProfiles/*/app-release.dm`;
  the API 31+ one is released as `tv-home.dm` and the updater adds it to the install session as `base.dm`
  → `speed-profile` at install. Local: `adb install-multiple -r app-release.apk baselineProfiles/<n>/app-release.dm`
  (`<n>` from `output-metadata.json`, minApi 31). Check: `dumpsys package dexopt | grep -A3 "\[com.ohav.tvhome\]"`.
- Restart the app process without `force-stop` (which drops the accessibility service): `am crash <pid>`.
- After the fix (classic layout, 2 cameras): idle 0 frames; D-pad ~6% janky, p99 ~60 ms (was 45–65%, p99 150 ms).
  PSS ~143 MB (odex + background layer).

## TV provider (Top Shelf)
- Needs `READ_EPG_DATA` (normal) **and** `android.permission.READ_TV_LISTINGS` (runtime on 14; `pm grant`).
- Non-privileged callers: no selection allowed → query all, filter in code. Preview channels exist for YouTube,
  Prime, Spotify, Stremio, SmartTube; Netflix uses Watch Next.

## Spotify Connect gotcha
- Spotify TV app runs a BLE GATT server + DIAL for Spotify Connect; nearby phones on the same account connect and
  can start playback on the Streamer (even with the TV off). Seen 2026-10-24 after a `monkey` test started playback.
- Fix: `am force-stop com.spotify.tv.android` (stays stopped until opened/cast) + user picks "This phone" in Spotify.
- Spotify has `com.spotify.androidtv.bootreceiver.BootReceiver` → starts at every boot and keeps a paused media
  session, so Google Home shows "Spotify" on the Streamer. User declined disabling the receiver (2026-10-26).
- Streamer never fully powers off (networked standby for Cast / Google Home hub); `mWakefulness=Asleep` is normal.

## Audio
- HDMI is a *full-volume* device; `mHdmiCecVolumeControlEnabled=false`; no absolute-volume devices.
  TV volume is changed by the remote's IR. Volume from phone needs HDMI-CEC volume control (untested).

## Stremio (com.stremio.one 1.11.2)
- Ships libmpv + libVLC + Media3 (+libass); has subtitle-delay code (`sub-delay`, `subtitlesDelay`). No external API.

## Cameras (L2)
- DVR `10.0.0.14` = TVT/Provision-ISR (`Server: Apache`, Digest realm "RTSP SERVER"); paths `/chID=N&streamType=main|sub`.
  8 channels; 5/7/8 send black frames (no camera). Main = 960x1080 (1080N), sub = 352x288 (CIF), H.264, no aspect
  info → always display 16:9 (`displayAspect`).
- **8 SurfaceView video layers at once crashed the vendor HWC** (`hwcomposer.mt8696` BlitJob SIGSEGV → soft reboot of
  the whole UI). Multi-video screens must use TextureView. One full-screen SurfaceView is fine.
- MediaTek `c2.mtk.avc.decoder`: ~4 instances, then `NO_MEMORY` → `setEnableDecoderFallback(true)` (software decoder).
  Without valid login every path returns 401, so the format can only be detected after login (`RtspProbe`).
- Card: Media3 RTSP (TCP) in `CameraService` (`:camera` process), rendering into the launcher's SurfaceView Surface
  passed over AIDL. Starts 1.2 s after home shows, stops on `onStop`. Full screen = `CameraActivity` (also `:camera`).
- Settings text fields: `TextRow` (row → field on OK) so D-pad browsing doesn't pop the keyboard; IME shows Latin.

## Self-update
- Releases `v1.0.<run>`; asset `tv-home.apk`; PackageInstaller session; first adb-installed copy needs a confirm dialog.
- `appops set com.ohav.tvhome REQUEST_INSTALL_PACKAGES allow` (install script).

## Wikimedia
- Requires a descriptive User-Agent with contact (robot policy) or returns 429; only standard thumb sizes (330px…).
