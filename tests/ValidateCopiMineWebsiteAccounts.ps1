$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$mainPy = Join-Path $root 'admin-web\backend\main.py'
$migration = Join-Path $root 'db\migrations\20260611_001_copimine_v4_postgres.sql'

$backend = Get-Content -Raw -Encoding UTF8 $mainPy
$sql = Get-Content -Raw -Encoding UTF8 $migration

$backendMarkers = @(
  '/api/player/register',
  '/api/player/login',
  '/api/player/me',
  '/api/player/link/request',
  '/api/player/link/confirm',
  'one_time_link_codes',
  'make_password_hash',
  'verify_password_hash',
  'make_token(account_id, "player"',
  'code_hash'
)

foreach ($marker in $backendMarkers) {
  if ($backend -notmatch [regex]::Escape($marker)) {
    throw "Missing website account backend marker: $marker"
  }
}

$migrationMarkers = @(
  'CREATE TABLE IF NOT EXISTS site_accounts',
  'CREATE TABLE IF NOT EXISTS player_web_accounts',
  'CREATE TABLE IF NOT EXISTS minecraft_account_links',
  'CREATE TABLE IF NOT EXISTS whitelist_account_links',
  'CREATE TABLE IF NOT EXISTS one_time_link_codes',
  'CREATE TABLE IF NOT EXISTS player_profile_cache',
  'CREATE TABLE IF NOT EXISTS player_settings'
)

foreach ($marker in $migrationMarkers) {
  if ($sql -notmatch [regex]::Escape($marker)) {
    throw "Missing website account migration marker: $marker"
  }
}

if ($backend -match 'minecraft_password') {
  throw 'Website account flow must not ask for or store a Minecraft/AuthMe plaintext password.'
}

Write-Host 'Website accounts validation passed: player auth, Minecraft one-time linking, and PostgreSQL tables are present.'
