@echo off
chcp 65001 >nul
setlocal
set "REPO=%~dp0.."
set "LAUNCHER=%REPO%\scripts\minecraft\StartCopiMineWavesLocal.ps1"
if not exist "%LAUNCHER%" goto :missing
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%LAUNCHER%" %*
set "CODE=%ERRORLEVEL%"
echo.
if not "%CODE%"=="0" echo Launch failed with exit code %CODE%.
echo Press any key to close this window.
pause >nul
exit /b %CODE%
:missing
echo Launcher not found: %LAUNCHER%
echo Restore this BAT from the CopiMine repository tools directory.
pause
exit /b 1