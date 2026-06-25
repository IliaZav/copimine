$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$adminWeb = Join-Path $root 'admin-web'
$bot = Join-Path $adminWeb 'backend\discord_bot.py'
$service = Join-Path $adminWeb 'deploy\copimine-discord-bot.service'

if (-not (Test-Path -LiteralPath $adminWeb)) {
  throw 'Active admin-web folder is missing; Discord services would not start after replacing /opt/copimine.'
}

if (-not (Test-Path -LiteralPath $bot)) {
  throw 'Active Discord bot module is missing: admin-web/backend/discord_bot.py'
}

if (-not (Test-Path -LiteralPath $service)) {
  throw 'Discord bot systemd unit is missing from admin-web/deploy.'
}

$botText = Get-Content -Raw -Encoding UTF8 $bot
$serviceText = Get-Content -Raw -Encoding UTF8 $service

$requiredTables = @(
  'cmv7_president_state',
  'cmv7_ar_balances',
  'cmv7_ballot_issues',
  'cmv731_votes',
  'cmv7_player_checks'
)

foreach ($table in $requiredTables) {
  if ($botText -notmatch [regex]::Escape($table)) {
    throw "Discord bot does not know the new AdminPlus table: $table"
  }
}

if ($botText -match 'plus_ar_balances') {
  throw 'Discord bot still references legacy plus_ar_balances instead of cmv7_ar_balances.'
}

if ($botText -notmatch 'def election_overview_snapshot\(') {
  throw 'Discord bot is missing an election overview snapshot for the new elections flow.'
}

if ($botText -notmatch 'DISCORD_ELECTIONS_STATUS_CHANNEL_ID') {
  throw 'Discord bot is missing configurable elections status channel support.'
}

if ($botText -notmatch 'DISCORD_ADMIN_ALERTS_CHANNEL_ID') {
  throw 'Discord bot is missing admin-only alerts channel support.'
}

$requiredV4SyncMarkers = @(
  'discord_status_state',
  'bridge_events',
  'status_channel_snapshots',
  'discord_notifications_log',
  'save_state_sync',
  'load_runtime_state'
)

foreach ($marker in $requiredV4SyncMarkers) {
  if ($botText -notmatch [regex]::Escape($marker)) {
    throw "Discord bot is missing V4 sync marker: $marker"
  }
}

if (($botText | Select-String -Pattern 'STATUS_OFFLINE_CONFIRM_SECONDS\s*=' -AllMatches).Matches.Count -ne 1) {
  throw 'Discord bot has duplicated STATUS_OFFLINE_CONFIRM_SECONDS assignment.'
}

if ($serviceText -notmatch 'WorkingDirectory=/opt/copimine/admin-web') {
  throw 'Discord bot service must run from /opt/copimine/admin-web.'
}

if ($serviceText -notmatch 'ExecStart=/opt/copimine/admin-web/.venv/bin/python -m backend\.discord_bot') {
  throw 'Discord bot service must execute backend.discord_bot from admin-web venv.'
}

Write-Host 'Discord integration validation passed: active admin-web and bot support new AdminPlus election/economy/admin tables.'
