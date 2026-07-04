param([string]$Archive = "", [string]$TargetRoot = "")
$ErrorActionPreference = "Stop"
if (-not $Archive) { throw "Usage: rollback.ps1 -Archive <zip> [-TargetRoot <path>]" }
if (-not $TargetRoot) { $TargetRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
if (-not (Test-Path $Archive)) { throw "Archive not found: $Archive" }
Expand-Archive -LiteralPath $Archive -DestinationPath $TargetRoot -Force
Write-Host "Rollback restored into $TargetRoot"
