$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$backend = Join-Path $root "admin-web\backend\main.py"
$text = (Get-Content -Raw -Encoding UTF8 -LiteralPath $backend) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

foreach ($needle in @(
  "def sync_auth_whitelist_state(",
  "def log_auth_login_check(",
  "def pg_record_auth_state(",
  "auth_users_imported",
  "auth_whitelist_sync",
  "auth_login_checks",
  "auth_effects_disable_audit",
  "player_auth_runtime",
  "admin_auth_runtime"
)) {
  if (-not $text.Contains($needle)) { $errors.Add("Data migration/auth flow marker missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("Data migration validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Data migration validation passed."
