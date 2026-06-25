$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$migration = Join-Path $root "db\migrations\20260611_001_copimine_v4_postgres.sql"
$text = (Get-Content -Raw -Encoding UTF8 -LiteralPath $migration) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

foreach ($needle in @(
  "CREATE TABLE IF NOT EXISTS elections",
  "CREATE TABLE IF NOT EXISTS election_decrees",
  "CREATE TABLE IF NOT EXISTS election_petitions",
  "CREATE TABLE IF NOT EXISTS discord_notifications_log",
  "CREATE TABLE IF NOT EXISTS bridge_events",
  "CREATE TABLE IF NOT EXISTS status_channel_snapshots",
  "CREATE TABLE IF NOT EXISTS auth_migration_state",
  "CREATE TABLE IF NOT EXISTS auth_whitelist_sync",
  "CREATE TABLE IF NOT EXISTS auth_login_checks",
  "CREATE TABLE IF NOT EXISTS auth_effects_disable_audit",
  "idx_bridge_events_created",
  "idx_status_channel_snapshots_created"
)) {
  if (-not $text.Contains($needle)) { $errors.Add("Migration missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("PostgreSQL schema validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "PostgreSQL schema validation passed."
