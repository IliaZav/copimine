$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$backend = Join-Path $root "admin-web\backend\main.py"
$java = (Get-Content -Raw -Encoding UTF8 -LiteralPath $plugin) -replace "`r", ""
$py = (Get-Content -Raw -Encoding UTF8 -LiteralPath $backend) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

if ($java.Contains("SQLite fallback is disabled.") -eq $false) { $errors.Add("Plugin must explicitly disable SQLite fallback for primary bank boot.") }
if ($java.Contains("CREATE TABLE IF NOT EXISTS cmv4_bank_accounts") -eq $false) { $errors.Add("Plugin bank storage must use V4 bank tables.") }
if ($py.Contains("cmv4_bank_accounts") -eq $false -or $py.Contains("cmv4_bank_ledger") -eq $false) { $errors.Add("Backend finance runtime must use cmv4_bank_* tables.") }
if ($py.Contains("legacyFallback") -eq $false) { $errors.Add("Backend should mark legacy plugin DB only as fallback in source metadata.") }

if ($errors.Count -gt 0) {
  throw ("No-SQLite-active-finance validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "No active SQLite finance validation passed."
