@echo off
setlocal EnableExtensions
title CopiMine local End Rift test

set "WORKTREE=D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event"
set "RUNNER=%WORKTREE%\tests\StartEndRiftLocalUserSession.ps1"

if not exist "%RUNNER%" (
    echo [ERROR] Local End Rift runner is missing:
    echo         %RUNNER%
    pause
    exit /b 1
)

echo Starting the current local CopiMine End Rift version...
set "RADMIN_IP="
for /f "delims=" %%A in ('powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%WORKTREE%\tools\Get-CopiMineRadminAddress.ps1"') do set "RADMIN_IP=%%A"
if defined RADMIN_IP (
    echo Server: %RADMIN_IP%:25566
    echo Resource pack: http://%RADMIN_IP%:8092/CopiMineResourcePack.zip
) else (
    echo Server: 127.0.0.1:25566 ^(Radmin VPN address not detected yet; runner will recheck^)
    echo Resource pack: http://127.0.0.1:8092/CopiMineResourcePack.zip
)
echo Website: http://127.0.0.1:8093/
echo PostgreSQL: 127.0.0.1:55433 / database copimine
echo Worktree: %WORKTREE%
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUNNER%" -AdminNickname "SudoKillDash9" -LaunchClient
set "exitCode=%ERRORLEVEL%"

if not "%exitCode%"=="0" (
    echo.
    echo [ERROR] Local session failed with exit code %exitCode%.
    echo Check local-runtime\user-session-logs, local-runtime\end-rift-server\logs\latest.log and the local website log.
    pause
    exit /b %exitCode%
)

echo.
echo [OK] Local website, PostgreSQL, server, all plugins, resource-pack HTTP and OP are ready.
if defined RADMIN_IP (echo Connect Minecraft and your friend to %RADMIN_IP%:25566 so the resource pack downloads.) else (echo Connect Minecraft to 127.0.0.1:25566; see runner output for the Radmin address.)
echo Open the local website at http://127.0.0.1:8093/ (localadmin / localadmin123).
pause
exit /b 0
