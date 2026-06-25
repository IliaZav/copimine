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

Require-Contains 'cmv7_polling_stations' 'Missing persistent polling station table.'
Require-Contains 'openPollingStations' 'Missing CIK polling station management GUI.'
Require-Contains 'openPollingStationHub' 'Missing public polling station hub for players.'
Require-Contains 'givePollingStationKit' 'Missing official station placement item.'
Require-Contains 'onPollingStationPlace' 'Missing interactive station placement handler.'
Require-Contains 'isPollingStationBlock' 'Missing station block detector.'
Require-Contains 'polling_station_kit' 'Station kit must be PDC-tagged.'
Require-Contains 'openCandidateDecision' 'Missing candidate decision GUI.'
Require-Contains 'openVoteConfirm' 'Missing vote confirmation GUI.'
Require-Contains 'sealBallotChoice' 'Missing commandless ballot sealing flow.'
Require-Contains 'depositSealedBallotAtStation' 'Missing physical ballot box deposit flow.'
Require-Contains 'vote-confirm:' 'Missing vote confirmation action.'
Require-Contains 'vote-seal:' 'Missing ballot seal action.'
Require-Contains 'vote-deposit:' 'Missing ballot deposit action.'
Require-Regex 'onInteract[\s\S]*isPollingStationBlock[\s\S]*openPollingStationHub[\s\S]*isBallotItem[\s\S]*openBallotCandidateHub' 'PlayerInteractEvent must prefer station interaction before ballot item fallback.'
Require-Regex 'sealBallotChoice[\s\S]*requireElectionStatus\([^;]+VOTING_OPEN[\s\S]*findOwnedUnusedBallot[\s\S]*selected_candidate' 'Interactive voting must first seal the selected candidate into an owned physical ballot.'
Require-Regex 'depositSealedBallotAtStation[\s\S]*hasCitizenVote[\s\S]*INSERT INTO cmv731_votes[\s\S]*UPDATE cmv7_ballot_issues SET used=1' 'Physical station deposit must prevent duplicates, write vote ledger, and mark the ballot used.'
Require-Regex 'openBallotCandidateHub[\s\S]*ballot-candidate:' 'Ballot candidate hub must route through a candidate decision screen.'
Require-Regex 'openPollingStationHub[\s\S]*open:station-ballot:' 'Polling station hub must let a player continue with their ballot without commands.'
Require-Contains 'STATION_RIGHT_CLICK_DEPOSIT_ONLY_V5' 'Polling station hub must explain physical sealed-ballot deposit.'
Require-Contains 'a.split(":",4)' 'Station-aware ballot actions must preserve election, candidate, and station context.'
Require-Regex 'open:station-ballot:[\s\S]*openBallotCandidateHub\(p,null,a\.substring' 'Station ballot action must keep the station id while opening candidates.'
Require-Regex 'vote-seal:[\s\S]*sealBallotChoice\(p,parts\[1\],parts\[2\],parts\.length>=4\?parts\[3\]:"inventory"\)' 'Final confirmation must persist the originating station id while sealing the ballot.'
Require-Regex 'vote-deposit:[\s\S]*depositSealedBallotAtStation\(p,findOwnedSealedBallot' 'Final vote counting must happen only when the sealed ballot is deposited.'
Require-Contains 'chair:station-kit' 'Chair panel must expose station kit issuing.'
Require-Contains 'open:polling-stations' 'Election menus must expose polling station management.'

if ($errors.Count -gt 0) {
  throw ("Interactive election RP validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Interactive election RP validation passed: polling stations, candidate decision, confirmation, and commandless vote casting are wired.'
