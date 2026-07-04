param([string]$ProjectRoot = "", [string]$ReleaseDir = "")
$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) { $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
if (-not $ReleaseDir) { $ReleaseDir = Join-Path (Split-Path -Parent (Split-Path -Parent $ProjectRoot)) "release\backups" }
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$stamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$zip = Join-Path $ReleaseDir "copimine-backup-$stamp.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $ProjectRoot "*") -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Backup: $zip"
