@echo off
setlocal
cd /d "%~dp0"

echo Starting the local CopiMine stack...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\local-stack.ps1" -Action start
set "exitCode=%ERRORLEVEL%"

if not "%exitCode%"=="0" (
    echo.
    echo Local stack failed to start. Check local-runtime\logs\stack.log
    pause
)

exit /b %exitCode%
