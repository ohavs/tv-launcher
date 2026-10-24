@echo off
rem Make Projectivy the Home screen (accessibility override on top of the stock launcher)
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set TV=10.0.0.43:5555
"%ADB%" connect %TV%
"%ADB%" -s %TV% shell pm enable com.google.android.apps.tv.launcherx
"%ADB%" -s %TV% shell pm enable com.google.android.tungsten.setupwraith
"%ADB%" -s %TV% shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
"%ADB%" -s %TV% shell settings put secure enabled_accessibility_services com.spocky.projengmenu/.services.ProjectivyAccessibilityService
"%ADB%" -s %TV% shell settings put secure accessibility_enabled 1
"%ADB%" -s %TV% shell am start -n com.spocky.projengmenu/.ui.home.MainActivity
echo Done: Projectivy home.
pause
