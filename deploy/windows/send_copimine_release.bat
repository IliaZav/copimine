@echo off
setlocal EnableExtensions

rem CopiMine release uploader. OpenSSH asks for the password/key itself;
rem credentials are intentionally not stored in this file.
set "SERVER_NAME=server-rpgrp"
set "SERVER_IP=100.108.97.11"
set "USERNAME=qwerty"
set "SSH_PORT=22"
set "REMOTE_DIR=/home/%USERNAME%/copimine-upload"

set "ARCHIVE=%~1"
if not defined ARCHIVE (
  for /f "usebackq delims=" %%F in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$root=Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Parent '%~dp0'))) 'release'; $f=Get-ChildItem -LiteralPath $root -Filter 'copimine-opt-full-*.tar.gz' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if($f){$f.FullName}"`) do set "ARCHIVE=%%F"
)
if not defined ARCHIVE (
  echo Usage: %~nx0 path\to\copimine-opt-full-*.tar.gz
  exit /b 2
)
if not exist "%ARCHIVE%" (
  echo Archive not found: %ARCHIVE%
  exit /b 2
)

set "ARCHIVE_NAME=%~nx1"
if "%~1"=="" for %%F in ("%ARCHIVE%") do set "ARCHIVE_NAME=%%~nxF"
set "SHA=%ARCHIVE%.sha256"
set "BOOTSTRAP=%ARCHIVE:.tar.gz=.bootstrap.json%"
set "INSTALLER=%~dp0..\ubuntu\install_release.sh"
if not exist "%SHA%" echo Missing checksum sidecar: %SHA% & exit /b 2
if not exist "%BOOTSTRAP%" echo Missing bootstrap manifest: %BOOTSTRAP% & exit /b 2
if not exist "%INSTALLER%" echo Missing installer wrapper: %INSTALLER% & exit /b 2

echo Uploading %ARCHIVE_NAME% to %USERNAME%@%SERVER_IP% (%SERVER_NAME%)...
ssh -p %SSH_PORT% %USERNAME%@%SERVER_IP% "mkdir -p '%REMOTE_DIR'"
if errorlevel 1 exit /b 1
scp -P %SSH_PORT% "%ARCHIVE%" "%SHA%" "%BOOTSTRAP%" "%INSTALLER%" "%USERNAME%@%SERVER_IP%:%REMOTE_DIR%/"
if errorlevel 1 exit /b 1

echo Starting verified install on Ubuntu. Existing data is preserved by default.
ssh -t -p %SSH_PORT% %USERNAME%@%SERVER_IP% "sudo bash '%REMOTE_DIR%/install_release.sh' '%REMOTE_DIR%/%ARCHIVE_NAME%'"
if errorlevel 1 exit /b 1
echo Release uploaded and installed.
endlocal
