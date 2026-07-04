$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$workspace = Split-Path -Parent (Split-Path -Parent $root)
$guide = Join-Path $workspace "COPIMINE_TRANSFER_GUIDE.txt"
$adminService = Join-Path $root "admin-web\deploy\copimine-admin.service"
$botService = Join-Path $root "admin-web\deploy\copimine-discord-bot.service"
$installScript = Join-Path $root "deploy\ubuntu\install.sh"
$updateScript = Join-Path $root "deploy\ubuntu\update.sh"
$verifyScript = Join-Path $root "deploy\ubuntu\verify.sh"
$sharedScript = Join-Path $root "deploy\shared\common.sh"
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($guide,$adminService,$botService,$installScript,$updateScript,$verifyScript,$sharedScript)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing deploy file: $path") }
}

$guideText = (Get-Content -Raw -Encoding UTF8 -LiteralPath $guide) -replace "`r", ""
foreach ($needle in @("deploy/ubuntu/install.sh","deploy/ubuntu/verify.sh","copimine-admin.service","PostgreSQL","Ubuntu")) {
  if (-not $guideText.Contains($needle)) { $errors.Add("Transfer guide missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("Ubuntu release structure validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Ubuntu release structure validation passed."
