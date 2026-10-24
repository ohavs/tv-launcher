@echo off
rem Undo everything: re-enable every debloated package, restore the stock Google TV home
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set TV=10.0.0.43:5555
"%ADB%" connect %TV%
for /f "usebackq eol=# tokens=1" %%p in ("%~dp0debloat.txt") do (
  echo enable %%p
  "%ADB%" -s %TV% shell pm enable %%p
)
call "%~dp0launcher-stock.cmd"
