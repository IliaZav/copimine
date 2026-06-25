$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 $source
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'createPollingStationFromTarget' 'Polling station GUI must create a station from the block the admin is looking at.'
Require-Contains 'targetPollingStationBlock' 'Target block selection must be isolated in one helper with distance/material checks.'
Require-Contains 'openPollingStationCard' 'Polling station GUI must have a per-station detail card.'
Require-Contains 'archivePollingStation' 'Polling station deletion must archive/deactivate safely instead of losing vote history.'
Require-Contains 'station:create-target' 'Polling station GUI must expose create-from-target action.'
Require-Contains 'station:card:' 'Polling station list must open a station detail card.'
Require-Contains 'station:delete-confirm:' 'Polling station detail card must use a confirmation screen before deletion.'
Require-Contains 'station:delete:' 'Polling station confirmed delete action must be wired.'
Require-Contains 'station:teleport:' 'Polling station detail card must expose a teleport action.'
Require-Contains 'ULTRA7_POLLING_STATION_CREATE_TARGET' 'Creating a station from target block must write audit.'
Require-Contains 'ULTRA7_POLLING_STATION_ARCHIVE' 'Deleting/archiving a station must write audit.'
Require-Contains 'ensureColumn("cmv7_polling_stations","archived_by"' 'Polling station table must preserve archive metadata.'
Require-Contains 'ensureColumn("cmv7_polling_stations","archived_at"' 'Polling station table must preserve archive timestamps.'
Require-Contains 'getTargetBlockExact(8)' 'Target block creation must use a bounded ray target.'
Require-Regex 'openPollingStations[\s\S]*station:create-target[\s\S]*station:card:' 'Polling station overview must show create-from-target and route rows into cards.'
Require-Regex 'openPollingStationCard[\s\S]*station:teleport:[\s\S]*station:delete-confirm:' 'Polling station card must expose teleport and delete confirmation.'
Require-Regex 'handle\(Player p[\s\S]*station:create-target[\s\S]*createPollingStationFromTarget[\s\S]*station:delete:[\s\S]*archivePollingStation' 'Polling station actions must be handled in GUI without commands.'
Require-Regex 'archivePollingStation[\s\S]*SELECT COUNT\(\*\) FROM cmv731_votes[\s\S]*UPDATE cmv7_polling_stations SET active=0' 'Deleting a station with vote history must deactivate/archive it safely.'

if ($errors.Count -gt 0) {
  throw ("Polling station GUI plus validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Polling station GUI plus validation passed: target creation, station cards, safe deletion, teleport and audit are wired.'
