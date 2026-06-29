$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$economy = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'
$migration = Join-Path $root 'db\migrations\20260611_001_copimine_v4_postgres.sql'

$java = Get-Content -Raw -Encoding UTF8 $economy
$sql = Get-Content -Raw -Encoding UTF8 $migration

$javaMarkers = @(
  'activeAtmBlocks',
  'atmIdsByBlock',
  'isAtmBlock(Block block)',
  'atmId(Block block)',
  'createBankAtmFromTargetAsync',
  'archiveBankAtmAsync',
  'ATM_EXISTS:',
  'openBankAtm(',
  'bank_pin_hashes',
  'account_lockouts',
  'PBKDF2WithHmacSHA256',
  'ATM_DEPOSIT',
  'ATM_WITHDRAW',
  'ATM_TRANSFER',
  'cmv4_bank_accounts',
  'cmv4_bank_ledger',
  'ar_atms',
  'ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS archived_by',
  'ALTER TABLE ar_atms ADD COLUMN IF NOT EXISTS archived_at',
  'protected_block_visuals',
  'FOR UPDATE'
)

foreach ($marker in $javaMarkers) {
  if ($java -notmatch [regex]::Escape($marker)) {
    throw "Missing bank ATM EconomyCore marker: $marker"
  }
}

$migrationMarkers = @(
  'CREATE TABLE IF NOT EXISTS ar_atms',
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

Write-Host 'Bank ATM validation passed: ATM runtime lives in EconomyCore with cached block detection, async create/archive flow, protected visuals, hashed PIN storage, and bank ledger markers.'
