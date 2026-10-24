# DEV-NOTES — device facts & gotchas (read when touching the related area)

## Device
- Google TV Streamer `kirkwood`, Android 14 (SDK 34), 4 GB RAM (~1.4 GB free with an app open), armeabi-v7a apps.
- ADB over network: `10.0.0.43:5555` (mDNS `_adb._tcp`). ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- Shell user has groups `input`, `uhid` — but SELinux blocks writing `/dev/input/*`.
- Open ports: 5555 adb, 6466/6467 Android TV Remote v2 (`com.google.android.tv.remote.service`), 8008/8009/8443 Cast.

## Testing rules
- **Never use `monkey` to launch apps** — it injects a random event (e.g. MEDIA_PLAY → Spotify starts playing).
  Launch with: `cmd package resolve-activity --brief -c android.intent.category.LEANBACK_LAUNCHER <pkg>` → `am start -n <component>`.
- `adb shell input keyevent` events are injected and **skip accessibility key filters**. To test like a real remote use
  the `hid` tool with a virtual remote: `tools/hid/make.py` (JSON → `/data/local/tmp`, `adb shell hid file.json`).
- Top activity: `dumpsys activity activities | grep -m1 topResumedActivity`.
- Screenshot: `adb exec-out screencap -p > backup/x.png` (backup/ is gitignored); downscale before viewing.
- Local builds: `BUILD_NUMBER=<next CI run number> ./gradlew :app:assembleRelease`, else adb refuses a versionCode downgrade.
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
- Accessibility enabled via adb: `settings put secure enabled_accessibility_services com.ohav.tvhome/com.ohav.tvhome.HomeKeyService`
  + `accessibility_enabled 1`. Sideloaded apps hit "restricted settings" in the UI; adb bypasses it. Survives app updates (verified).
- Remote key layout `/system/usr/keylayout/Vendor_18d1_Product_9451.kl`: MACRO_1, BUTTON_3 (YouTube key), BUTTON_4 (Netflix key).

## TV provider (Top Shelf)
- Needs `READ_EPG_DATA` (normal) **and** `android.permission.READ_TV_LISTINGS` (runtime on 14; `pm grant`).
- Non-privileged callers: no selection allowed → query all, filter in code. Preview channels exist for YouTube,
  Prime, Spotify, Stremio, SmartTube; Netflix uses Watch Next.

## Spotify Connect gotcha
- Spotify TV app runs a BLE GATT server + DIAL for Spotify Connect; nearby phones on the same account connect and
  can start playback on the Streamer (even with the TV off). Seen 2026-10-24 after a `monkey` test started playback.
- Fix: `am force-stop com.spotify.tv.android` (stays stopped until opened/cast) + user picks "This phone" in Spotify.

## Audio
- HDMI is a *full-volume* device; `mHdmiCecVolumeControlEnabled=false`; no absolute-volume devices.
  TV volume is changed by the remote's IR. Volume from phone needs HDMI-CEC volume control (untested).

## Stremio (com.stremio.one 1.11.2)
- Ships libmpv + libVLC + Media3 (+libass); has subtitle-delay code (`sub-delay`, `subtitlesDelay`). No external API.

## Self-update
- Releases `v1.0.<run>`; asset `tv-home.apk`; PackageInstaller session; first adb-installed copy needs a confirm dialog.
- `appops set com.ohav.tvhome REQUEST_INSTALL_PACKAGES allow` (install script).

## Wikimedia
- Requires a descriptive User-Agent with contact (robot policy) or returns 429; only standard thumb sizes (330px…).
