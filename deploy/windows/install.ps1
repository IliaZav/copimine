param([string]$ProjectRoot = "")
$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) { $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
$script = Join-Path $ProjectRoot "scripts\package_full_release.ps1"
powershell -ExecutionPolicy Bypass -File $script -ProjectRoot $ProjectRoot
