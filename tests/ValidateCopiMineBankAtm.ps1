$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$plugin = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$migration = Join-Path $root 'db\migrations\20260611_001_copimine_v4_postgres.sql'

$java = Get-Content -Raw -Encoding UTF8 $plugin
$sql = Get-Content -Raw -Encoding UTF8 $migration

$javaMarkers = @(
  'V4_BANK_ATM_GAMEPLAY',
  'openBankAtms',
  'openBankAtm',
  'isBankAtmBlock',
  'bankAtmId',
  'ULTRA7_BANK_ATM_BREAK_BLOCKED',
  'bank:deposit-hand:',
  'bank:deposit-all:',
  'bank:withdraw-pin:',
  'bank:transfer-targets:',
  'bank:transfer-pin:',
  'bankpin:confirm',
  'ATM_DEPOSIT',
  'ATM_WITHDRAW',
  'ATM_TRANSFER',
  'cmv4_bank_transfers',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'PBKDF2WithHmacSHA256',
  'bank_pin_hashes',
  'bankPinMustChange',
  'failed_pin_attempts',
  'account_lockouts',
  'PIN_MAX_ATTEMPTS',
  'PIN_LOCKOUT',
  'ar_atms',
  'atm_events',
  'cmv4_bank_ledger',
  'FOR UPDATE',
  'bankAccountId(String uuid){return "ar:"'
)

foreach ($marker in $javaMarkers) {
  if ($java -notmatch [regex]::Escape($marker)) {
    throw "Missing bank ATM Java marker: $marker"
  }
}

$migrationMarkers = @(
  'CREATE TABLE IF NOT EXISTS ar_atms',
  'CREATE TABLE IF NOT EXISTS atm_events',
  'CREATE TABLE IF NOT EXISTS atm_sessions',
  'CREATE TABLE IF NOT EXISTS atm_audit',
  'CREATE TABLE IF NOT EXISTS bank_pin_hashes',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_accounts',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_ledger'
)

foreach ($marker in $migrationMarkers) {
  if ($sql -notmatch [regex]::Escape($marker)) {
    throw "Missing bank ATM migration marker: $marker"
  }
}

if ($java -match 'pin_plain|plain_pin|plaintext_pin|pin_text') {
  throw 'ATM PIN must not be stored or logged as plaintext.'
}

Write-Host 'Bank ATM validation passed: in-game ATM, PostgreSQL bank ledger, PIN hash verification, and audit markers are present.'
