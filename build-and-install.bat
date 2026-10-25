@echo off
rem Setka: build the debug APK and install it on the connected phone (USB debugging on).
cd /d %~dp0
if not exist .gradle\moved_to_d.flag (
  call gradlew.bat clean --no-configuration-cache --console=plain
  echo ok> .gradle\moved_to_d.flag
)
call gradlew.bat :app:installGplayDebug --console=plain
if errorlevel 1 (
  echo.
  echo BUILD FAILED - send the first "e: ..." lines above to Claude.
  pause
  exit /b 1
)
adb shell monkey -p com.setkamatrix.android.debug -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo Done.
pause
