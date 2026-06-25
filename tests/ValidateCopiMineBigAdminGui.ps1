$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'

$text = Get-Content -Raw -Encoding UTF8 $source
$plugin = Get-Content -Raw -Encoding UTF8 $pluginYml
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add($message)
  }
}

Require-Contains '9.1.0-postgres-v4' 'AdminPlus version must be bumped for the PostgreSQL V4 release.'
if (-not $plugin.Contains("9.1.0-postgres-v4")) {
  $errors.Add('plugin.yml must advertise 9.1.0-postgres-v4.')
}

Require-Regex 'private void openHub\(Player p\)(?:(?!private void openAdminMap)[\s\S])*create\(m,27,"&2&lCopiMine' 'Main admin hub must be a compact 27-slot three-button entry screen.'
Require-Regex 'private void openHub\(Player p\)(?:(?!private void openAdminMap)[\s\S])*"open:elections"(?:(?!private void openAdminMap)[\s\S])*"open:economy"(?:(?!private void openAdminMap)[\s\S])*"open:players"' 'Hub must expose exactly the three required top-level sections.'
Require-Regex 'openBallotCandidateHub\(Player p, ItemStack ballot, String stationId\)(?:(?!private void openCandidateApplicationPreview)[\s\S])*create\(m,27' 'Ballot candidate GUI must stay compact and readable.'
Require-Regex 'openPollingStationHub\(Player p,Block block\)(?:(?!private void sendPollingStationCitizenInfo)[\s\S])*create\(m,27' 'Polling station GUI must stay compact and readable.'

Require-Contains 'BIG_ADMIN_NAV_RAIL' 'Shared large-GUI navigation rail marker is missing.'
Require-Contains 'private void navRail(Menu m,String refresh)' 'Shared navigation rail helper is missing.'
Require-Contains 'private void btnIfNoAction' 'Navigation rail must use a safe no-overwrite button helper.'
Require-Regex 'btnIfNoAction[\s\S]*m\.actions\.containsKey\(slot\)' 'Safe navigation helper must not overwrite existing menu actions.'
Require-Regex 'private void nav\(Menu m,String back,String refresh\)[\s\S]*navRail\(m,refresh\)' 'Every standard admin tab must receive the shared big navigation rail.'

foreach ($action in @(
  'open:admin-map',
  'open:startup-readiness',
  'open:db-health',
  'open:elections',
  'open:economy',
  'open:players'
)) {
  Require-Regex ('navRail[\s\S]*"' + [regex]::Escape($action) + '"') "Navigation rail must expose $action."
}

Require-Regex 'openVoteConfirm[\s\S]*create\(m,54,[\s\S]*open:station-ballot' 'Vote confirmation must be expanded to a full, navigable ballot screen.'
Require-Regex 'openPollingStationDeleteConfirm[\s\S]*create\(m,54,' 'Polling station delete confirmation must be a large safe-confirmation GUI.'

if ($errors.Count -gt 0) {
  Write-Host 'Big admin GUI validation FAILED:' -ForegroundColor Red
  foreach ($e in $errors) { Write-Host " - $e" -ForegroundColor Red }
  exit 1
}

Write-Host 'Big admin GUI validation passed: compact entry/player-facing screens and large internal admin rails are wired.' -ForegroundColor Green
