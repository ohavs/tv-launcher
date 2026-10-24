@echo off
rem Back to the stock Google TV home screen (re-enables it, turns off other launchers' overrides)
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set TV=10.0.0.43:5555
"%ADB%" connect %TV%
"%ADB%" -s %TV% shell pm enable com.google.android.apps.tv.launcherx
"%ADB%" -s %TV% shell pm enable com.google.android.tungsten.setupwraith
"%ADB%" -s %TV% shell settings delete secure enabled_accessibility_services
"%ADB%" -s %TV% shell settings put secure accessibility_enabled 0
"%ADB%" -s %TV% shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
"%ADB%" -s %TV% shell input keyevent KEYCODE_HOME
echo Done: stock Google TV home.
pause
