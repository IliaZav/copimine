@echo off
setlocal
cd /d "%~dp0"

echo Stopping the local CopiMine stack...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\local-stack.ps1" -Action stop
set "exitCode=%ERRORLEVEL%"

if not "%exitCode%"=="0" (
    echo.
    echo Local stack did not stop cleanly. Check local-runtime\logs\stack.log
    pause
)

exit /b %exitCode%
