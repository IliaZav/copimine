$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$mainPy = Join-Path $root 'admin-web\backend\main.py'
$discordBot = Join-Path $root 'admin-web\backend\discord_bot.py'
$migration = Join-Path $root 'db\migrations\20260611_001_copimine_v4_postgres.sql'

$pluginText = Get-Content -Raw -Encoding UTF8 $source
$mainText = Get-Content -Raw -Encoding UTF8 $mainPy
$botText = Get-Content -Raw -Encoding UTF8 $discordBot
$migrationText = Get-Content -Raw -Encoding UTF8 $migration

$requiredPluginMarkers = @(
  'org.postgresql.Driver',
  'PgConnectionPool',
  'POSTGRES_PASSWORD',
  'statement_timeout',
  'lock_timeout',
  'ArrayBlockingQueue',
  'refreshSidebarSnapshotAsync',
  'sidebarRefreshInFlight',
  'dbAsync("AR placed block record"',
  'dbAsync("player check start"',
  'ON CONFLICT(uuid) DO UPDATE'
)

foreach ($marker in $requiredPluginMarkers) {
  if ($pluginText -notmatch [regex]::Escape($marker)) {
    throw "Missing PostgreSQL/plugin optimization marker: $marker"
  }
}

$requiredIndexes = @(
  'idx_cmv7_candidates_election_uuid',
  'idx_cmv7_candidates_election_total',
  'idx_cmv7_applications_election_status',
  'idx_cmv7_ballot_issues_election_voter',
  'idx_cmv731_votes_election_voter',
  'idx_cmv731_sessions_election_voter',
  'idx_cmv7_ar_balances_balance',
  'idx_cmv7_ar_events_time',
  'idx_cmv7_player_checks_active_player',
  'idx_cmv7_president_state_active',
  'idx_cmv4_bank_ledger_account_time',
  'ux_cmv4_bank_transfers_idempotency'
)

foreach ($index in $requiredIndexes) {
  if ($pluginText -notmatch [regex]::Escape($index)) {
    throw "Missing PostgreSQL index for AdminPlus performance: $index"
  }
}

if ($pluginText -match 'DriverManager\.getConnection\("jdbc:sqlite') {
  throw 'SQLite fallback is still present in active plugin connection path.'
}

$requiredWebMarkers = @(
  'psycopg.connect',
  'SimplePgPool',
  'POSTGRES_POOL_MIN_SIZE',
  'POSTGRES_POOL_MAX_SIZE',
  'POSTGRES_SCHEMA',
  'PgCompatConnection',
  'admin_plugin_db_location',
  'SET statement_timeout',
  'CREATE TABLE IF NOT EXISTS election_decrees',
  'CREATE TABLE IF NOT EXISTS discord_notifications_log',
  'CREATE TABLE IF NOT EXISTS auth_login_checks'
)

foreach ($marker in $requiredWebMarkers) {
  if ($mainText -notmatch [regex]::Escape($marker)) {
    throw "Missing PostgreSQL/admin-web marker: $marker"
  }
}

$requiredBotMarkers = @(
  'psycopg.connect',
  'PgCompatConnection',
  'POSTGRES_PASSWORD',
  'ON CONFLICT DO NOTHING'
)

foreach ($marker in $requiredBotMarkers) {
  if ($botText -notmatch [regex]::Escape($marker)) {
    throw "Missing PostgreSQL/discord marker: $marker"
  }
}

$requiredMigrationTables = @(
  'CREATE TABLE IF NOT EXISTS cmv4_players',
  'CREATE TABLE IF NOT EXISTS cm_admin_users',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_accounts',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_ledger',
  'CREATE TABLE IF NOT EXISTS cmv4_bank_transfers',
  'CREATE TABLE IF NOT EXISTS elections',
  'CREATE TABLE IF NOT EXISTS election_decrees',
  'CREATE TABLE IF NOT EXISTS election_petitions',
  'CREATE TABLE IF NOT EXISTS discord_notifications_log',
  'CREATE TABLE IF NOT EXISTS bridge_events',
  'CREATE TABLE IF NOT EXISTS status_channel_snapshots',
  'CREATE TABLE IF NOT EXISTS auth_migration_state',
  'CREATE TABLE IF NOT EXISTS auth_users_imported',
  'CREATE TABLE IF NOT EXISTS auth_whitelist_sync',
  'CREATE TABLE IF NOT EXISTS auth_login_checks',
  'CREATE TABLE IF NOT EXISTS auth_effects_disable_audit',
  'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_ledger_idempotency'
)

foreach ($marker in $requiredMigrationTables) {
  if ($migrationText -notmatch [regex]::Escape($marker)) {
    throw "Missing migration marker: $marker"
  }
}

Write-Host 'Database optimization validation passed: PostgreSQL pool, timeouts, indexes, migrations, and async DB paths are present.'
