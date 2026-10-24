@echo off
rem Install/update TV Home and make it the Home screen.
rem Usage: install-launcher.cmd [path\to.apk]   (default: local build, else latest GitHub release)
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set TV=10.0.0.43:5555
set PKG=com.ohav.tvhome
set APK=%~1
if "%APK%"=="" set APK=%~dp0app\build\outputs\apk\release\app-release.apk
if not exist "%APK%" (
  echo Downloading latest release...
  set APK=%TEMP%\tv-home.apk
  curl -sfL -o "%TEMP%\tv-home.apk" https://github.com/ohavs/tv-launcher/releases/latest/download/tv-home.apk || (echo Download failed & exit /b 1)
)
"%ADB%" connect %TV%
"%ADB%" -s %TV% install -r "%APK%" || (echo Install failed & exit /b 1)
"%ADB%" -s %TV% shell appops set %PKG% REQUEST_INSTALL_PACKAGES allow
"%ADB%" -s %TV% shell pm grant %PKG% android.permission.READ_TV_LISTINGS
rem Projectivy's accessibility override would grab the Home button
"%ADB%" -s %TV% shell settings delete secure enabled_accessibility_services
"%ADB%" -s %TV% shell settings put secure accessibility_enabled 0
"%ADB%" -s %TV% shell cmd package set-home-activity --user 0 %PKG%/.MainActivity
"%ADB%" -s %TV% shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
"%ADB%" -s %TV% shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
"%ADB%" -s %TV% shell cmd package set-home-activity --user 0 %PKG%/.MainActivity
"%ADB%" -s %TV% shell am force-stop com.android.tv.settings
"%ADB%" -s %TV% shell input keyevent KEYCODE_HOME
echo Done: TV Home is the Home screen.
