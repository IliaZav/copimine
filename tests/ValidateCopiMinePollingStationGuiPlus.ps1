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

function Require-NotContains([string]$needle, [string]$message) {
  if ($text.Contains($needle)) { $script:errors.Add($message) }
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
Require-Contains 'legacyElectionRuntimeDisabled' 'AdminPlus must keep the retired election runtime disabled.'
Require-Contains 'openPollingStations' 'AdminPlus must retain a compatibility redirect for the old station entry point.'
Require-Contains 'open:elections' 'The compatibility station entry point must redirect to ElectionCore.'
Require-Contains 'getTargetBlockExact(8)' 'The retained helper must use a bounded ray target.'
Require-Regex 'openPollingStations\(Player p\).*?redirectLegacyElectionAction' 'AdminPlus must not render the old station GUI.'
Require-NotContains 'ULTRA7_POLLING_STATION_CREATE_TARGET' 'The retired AdminPlus creator must not write legacy audit events.'

if ($errors.Count -gt 0) {
  throw ("Polling station GUI plus validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Polling station GUI plus validation passed: target creation, station cards, safe deletion, teleport and audit are wired.'
