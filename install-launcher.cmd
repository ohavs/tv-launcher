@echo off
rem Install/update TV Home and make it the Home screen.
rem Usage: install-launcher.cmd [path\to.apk]   (default: local build, else latest GitHub release)
rem The stock launcher stays enabled (Google Home panel + dashboard need it); TV Home's
rem accessibility service takes over the Home button.
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
"%ADB%" -s %TV% shell pm enable com.google.android.apps.tv.launcherx
"%ADB%" -s %TV% shell pm enable com.google.android.tungsten.setupwraith
"%ADB%" -s %TV% shell cmd package set-home-activity --user 0 %PKG%/.MainActivity
"%ADB%" -s %TV% shell settings put secure enabled_accessibility_services %PKG%/%PKG%.HomeKeyService
"%ADB%" -s %TV% shell settings put secure accessibility_enabled 1
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity
echo Done: TV Home is the Home screen.
