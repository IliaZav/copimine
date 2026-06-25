$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()
$sql = (Get-ChildItem -LiteralPath (Join-Path $root 'db\migrations') -Filter '*.sql' | ForEach-Object { Get-Content -Raw -Encoding UTF8 $_.FullName }) -join "`n"
$admin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$schemaText = $sql + "`n" + $admin

foreach ($marker in @(
  'CREATE TABLE IF NOT EXISTS plugin_events',
  'CREATE TABLE IF NOT EXISTS cmv7_ar_balances',
  'CREATE TABLE IF NOT EXISTS cmv7_ar_transactions',
  'CREATE TABLE IF NOT EXISTS bank_pin_hashes',
  'CREATE TABLE IF NOT EXISTS artifact_items_catalog',
  'CREATE TABLE IF NOT EXISTS artifact_purchases',
  'CREATE TABLE IF NOT EXISTS artifact_pending_deliveries',
  'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_ballot_issues_active_once',
  'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_polling_stations_location_active'
)) {
  if ($schemaText -notmatch [regex]::Escape($marker)) { $errors.Add("Schema marker missing: $marker") }
}

if ($errors.Count -gt 0) { throw ("PostgreSQL schema sync validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'PostgreSQL schema sync validation passed.'
