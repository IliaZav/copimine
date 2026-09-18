@echo off
setlocal EnableExtensions
title CopiMine local End Rift test (Porthole)

set "WORKTREE=D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event"
set "RUNNER=%WORKTREE%\tests\StartEndRiftLocalUserSession.ps1"
set "RADMIN_HELPER=%WORKTREE%\tools\Get-CopiMineRadminAddress.ps1"
if not defined COPIMINE_NETWORK_MODE set "COPIMINE_NETWORK_MODE=porthole"

if /i not "%COPIMINE_NETWORK_MODE%"=="porthole" if /i not "%COPIMINE_NETWORK_MODE%"=="radmin" if /i not "%COPIMINE_NETWORK_MODE%"=="local" (
    echo [ERROR] COPIMINE_NETWORK_MODE must be porthole, radmin or local.
    if /i not "%COPIMINE_NO_PAUSE%"=="1" pause
    exit /b 2
)

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Windows PowerShell is not available on PATH.
    if /i not "%COPIMINE_NO_PAUSE%"=="1" pause
    exit /b 1
)

if not exist "%RUNNER%" (
    echo [ERROR] Local End Rift runner is missing:
    echo         %RUNNER%
    if /i not "%COPIMINE_NO_PAUSE%"=="1" pause
    exit /b 1
)
if /i "%COPIMINE_NETWORK_MODE%"=="radmin" if not exist "%RADMIN_HELPER%" (
    echo [ERROR] Radmin VPN helper is missing:
    echo         %RADMIN_HELPER%
    if /i not "%COPIMINE_NO_PAUSE%"=="1" pause
    exit /b 1
)

echo Starting the current local CopiMine End Rift version...
set "RUNNER_NETWORK_ARGS="
if /i "%COPIMINE_NETWORK_MODE%"=="porthole" (
    set "RUNNER_NETWORK_ARGS=-Porthole"
    echo Network: Porthole
    echo Porthole share ^(TCP only^): local 25566 -^> friend 25566 ^(Minecraft^)
    echo Porthole share ^(TCP only^): local 8092 -^> friend 8092 ^(resource pack^)
    echo Porthole share ^(UDP^): local 24454 -^> friend 24454 ^(Simple Voice Chat^)
    echo Friend Direct Connect: 127.0.0.1:25566
    echo Do not share RCON 25576, website 8093 or PostgreSQL 55433.
) else if /i "%COPIMINE_NETWORK_MODE%"=="radmin" (
    set "RADMIN_IP="
    for /f "delims=" %%A in ('powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%WORKTREE%\tools\Get-CopiMineRadminAddress.ps1"') do set "RADMIN_IP=%%A"
    if defined RADMIN_IP (
        echo Network: Radmin VPN
        echo Server: %RADMIN_IP%:25566
        echo Resource pack: http://%RADMIN_IP%:8092/CopiMineResourcePack.zip
    ) else (
        echo Server: 127.0.0.1:25566 ^(Radmin VPN address not detected yet; runner will recheck^)
        echo Resource pack: http://127.0.0.1:8092/CopiMineResourcePack.zip
    )
) else (
    set "RADMIN_IP="
    echo Network: local machine only
    echo Server: 127.0.0.1:25566
    echo Resource pack: http://127.0.0.1:8092/CopiMineResourcePack.zip
)
echo Website: http://127.0.0.1:8093/
echo PostgreSQL: 127.0.0.1:55433 / database copimine
echo Worktree: %WORKTREE%
echo.

if /i "%COPIMINE_NO_CLIENT%"=="1" (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUNNER%" -AdminNickname "SudoKillDash9" %RUNNER_NETWORK_ARGS%
) else (
    if /i "%COPIMINE_LAUNCH_CLIENT%"=="1" (
        powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUNNER%" -AdminNickname "SudoKillDash9" %RUNNER_NETWORK_ARGS% -LaunchClient
    ) else (
        powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUNNER%" -AdminNickname "SudoKillDash9" %RUNNER_NETWORK_ARGS%
    )
)
set "exitCode=%ERRORLEVEL%"

if not "%exitCode%"=="0" (
    echo.
    echo [ERROR] Local session failed with exit code %exitCode%.
    echo Check local-runtime\user-session-logs, local-runtime\end-rift-server\logs\latest.log and the local website log.
    if /i not "%COPIMINE_NO_PAUSE%"=="1" pause
    exit /b %exitCode%
)

echo.
echo [OK] Local website, PostgreSQL, server, all plugins, resource-pack HTTP and OP are ready.
if /i "%COPIMINE_NETWORK_MODE%"=="porthole" (
    echo Porthole is ready: share TCP 25566, TCP 8092 and UDP 24454 with your friend.
    echo Friend enters 127.0.0.1:25566 in Direct Connect after accepting both ports.
) else if defined RADMIN_IP (echo Connect Minecraft and your friend to %RADMIN_IP%:25566 so the resource pack downloads.) else (echo Connect Minecraft to 127.0.0.1:25566.)
echo Open the local website at http://127.0.0.1:8093/ (localadmin / localadmin123).
if /i not "%COPIMINE_NO_PAUSE%"=="1" pause
exit /b 0
