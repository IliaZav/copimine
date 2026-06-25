$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$backend = Join-Path $root "admin-web\backend\main.py"
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$py = (Get-Content -Raw -Encoding UTF8 -LiteralPath $backend) -replace "`r", ""
$java = (Get-Content -Raw -Encoding UTF8 -LiteralPath $plugin) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

foreach ($needle in @(
  "conn.commit()",
  "conn.rollback()",
  "INSERT INTO cmv4_bank_transfers",
  "INSERT INTO cmv4_bank_ledger",
  "status TEXT NOT NULL DEFAULT 'COMMITTED'"
)) {
  if (-not ($py + "`n" + $java).Contains($needle)) { $errors.Add("Transaction marker missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("DB transaction validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "DB transaction validation passed."
