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

$requiredMarkers = @(
  'ALLOW_LEGACY_ELECTION_FALLBACK = False',
  'def election_overview_snapshot(',
  'def election_overview_snapshot_v2(',
  'ELECTIONS_STATUS_CHANNEL_ID',
  'ADMIN_ALERTS_CHANNEL_ID',
  'discord_status_state',
  'bridge_events',
  'status_channel_snapshots',
  'save_state_sync',
  'load_runtime_state'
)

foreach ($marker in $requiredMarkers) {
  if ($botText -notmatch [regex]::Escape($marker)) {
    throw "Discord bot missing marker: $marker"
  }
}

if ($botText -match 'ALLOW_LEGACY_ELECTION_FALLBACK\s*=\s*True') {
  throw 'Discord bot must keep legacy election fallback disabled by default.'
}

if ($botText -match 'voter_name|voter_uuid|ballot_id[\s\S]{0,200}candidate_name') {
  throw 'Discord bot must not publish voter/candidate/ballot secrecy leaks.'
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

Write-Host 'Discord integration validation passed: admin-web bot remains active, legacy election fallback is disabled, and runtime state markers are present.'
