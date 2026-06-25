$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$mainPy = Join-Path $root 'admin-web\backend\main.py'
$appJs = Join-Path $root 'admin-web\frontend\assets\app.js'
$migration = Join-Path $root 'db\migrations\20260611_001_copimine_v4_postgres.sql'

$backend = Get-Content -Raw -Encoding UTF8 $mainPy
$frontend = Get-Content -Raw -Encoding UTF8 $appJs
$sql = Get-Content -Raw -Encoding UTF8 $migration

$backendMarkers = @(
  '/api/player/bank',
  '/api/player/bank/pin',
  '/api/player/bank/transfer',
  '/api/players/{player}/bank-pin/reset',
  'bank_pin_hashes',
  'temporary_pin_resets',
  'pin_reset_audit',
  'failed_pin_attempts',
  'account_lockouts',
  'PIN_MAX_ATTEMPTS',
  'PIN_LOCKOUT',
  'verify_bank_pin',
  'bankPinState',
  'playerResetBankPin',
  'SELECT * FROM cmv4_bank_accounts WHERE account_id=%s FOR UPDATE',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'make_password_hash(new_pin)'
)

foreach ($marker in $backendMarkers) {
  $present = $backend -match [regex]::Escape($marker) -or $frontend -match [regex]::Escape($marker)
  if (-not $present) {
    throw "Missing website bank backend marker: $marker"
  }
}

$migrationMarkers = @(
  'CREATE TABLE IF NOT EXISTS bank_pin_hashes',
  'CREATE TABLE IF NOT EXISTS temporary_pin_resets',
  'CREATE TABLE IF NOT EXISTS pin_reset_audit',
  'CREATE TABLE IF NOT EXISTS failed_pin_attempts',
  'CREATE TABLE IF NOT EXISTS security_events',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_accounts',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_ledger',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_transfers',
  'CREATE TABLE IF NOT EXISTS ar_accounts',
  'CREATE TABLE IF NOT EXISTS ar_ledger',
  'CREATE TABLE IF NOT EXISTS ar_atms',
  'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_transfers_idempotency'
)

foreach ($marker in $migrationMarkers) {
  if ($sql -notmatch [regex]::Escape($marker)) {
    throw "Missing website bank migration marker: $marker"
  }
}

if ($backend -match 'pin_plain|plain_pin|PIN=.*Discord') {
  throw 'PIN must not be stored or sent as plaintext.'
}

Write-Host 'Website bank validation passed: PIN hashes, player balance, transfer ledger, and PostgreSQL tables are present.'
