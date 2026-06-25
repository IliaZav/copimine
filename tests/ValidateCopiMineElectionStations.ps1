$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$migration = Join-Path $root 'db\migrations\20260612_006_elections_hardening.sql'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $migration
$errors = New-Object System.Collections.Generic.List[string]

function Require([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}
function RequireRx([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require $text 'openPollingStations' 'Polling stations admin GUI is missing.'
Require $text 'openPollingStationHub' 'Polling station player GUI is missing.'
Require $text 'pollingStationId' 'Polling station block id helper is missing.'
Require $text 'station:create-target' 'Station create action is missing.'
Require $text 'cmv7_polling_stations' 'Polling station table is missing.'
RequireRx $text 'onInteract[\s\S]*pollingStationId\(e\.getClickedBlock\(\)\)' 'Right-click on station block must route to station logic.'
RequireRx $text 'depositSealedBallotAtStation[\s\S]*stationId' 'Vote deposit must record station id.'
Require $sql 'ux_cmv7_polling_stations_location_active' 'Unique active station location index is missing.'
Require $text 'ELECTION_STATION_DISABLED' 'Station disabled error code is missing.'

if ($errors.Count -gt 0) {
  throw ("Election stations validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election stations validation passed.'
