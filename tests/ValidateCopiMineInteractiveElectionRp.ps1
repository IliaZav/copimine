$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
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

Require-Contains 'polling_stations' 'Missing persistent polling station table.'
Require-Contains 'openStationsMenu' 'Missing CIK polling station management GUI.'
Require-Contains 'createPollingStationFromTarget' 'Missing target-block station creation handler.'
Require-Contains 'openStationAccessMenu' 'Missing public station access router.'
Require-Contains 'openChairStationMenu' 'Missing chair station GUI.'
Require-Contains 'sendStationInfoToPlayer' 'Missing player-only station info message.'
Require-Contains 'station:self-chair:' 'Missing self-chair action.'
Require-Contains 'apply:station:self-chair:' 'Missing self-chair confirmation action.'
Require-Contains 'chair:issue-seal:' 'Chair panel must expose seal issuing/refresh.'
Require-Contains 'chair:request-removal:' 'Chair panel must expose removal request flow.'
Require-Contains 'cik_chair_removal_requests' 'Missing chair removal request table.'
Require-Contains 'chairreq:approve:' 'Admin GUI must approve chair removal requests.'
Require-Contains 'chairreq:reject:' 'Admin GUI must reject chair removal requests.'
Require-Contains 'depositBallot' 'Missing physical ballot deposit flow.'
Require-Contains 'confirmBallotChoice' 'Missing ballot confirmation flow.'
Require-Contains 'apply:vote:confirm:' 'Final vote confirmation must be explicit.'
Require-Regex 'onInteract[\s\S]*openStationAccessMenu[\s\S]*openChairStationMenu[\s\S]*sendStationInfoToPlayer' 'PlayerInteractEvent must route station clicks by role/state.'
Require-Regex 'confirmBallotChoice[\s\S]*confirmed_candidate_uuid' 'Interactive voting must confirm the selected candidate into an owned physical ballot.'
Require-Regex 'depositBallot[\s\S]*INSERT INTO votes[\s\S]*UPDATE ballots SET status' 'Physical station deposit must write vote ledger and mark the ballot used.'
Require-Regex 'openStationAccessMenu[\s\S]*station:access:admin:[\s\S]*station:access:chair:[\s\S]*station:access:info:' 'Station access menu must separate admin, chair and citizen clicks.'
Require-Contains 'open:stations' 'Election menus must expose polling station management.'

if ($errors.Count -gt 0) {
  throw ("Interactive election RP validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Interactive election RP validation passed: polling stations, candidate decision, confirmation, and commandless vote casting are wired."
