$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$errors = New-Object System.Collections.Generic.List[string]

function Require-Text([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}
function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

foreach ($needle in @(
  'openElections',
  'openApplicationsReview',
  'reviewApplication',
  'promoteApplicationCandidate',
  'openPollingStations',
  'issueBallot',
  'sealBallotChoice',
  'depositSealedBallotAtStation',
  'syncCandidateVotesFromLedger',
  'assignPresident',
  'SidebarSnapshot',
  'refreshSidebarSnapshotAsync'
)) {
  Require-Text $needle "Missing election system component: $needle"
}

foreach ($code in @(
  'ELECTION_STAGE_INVALID',
  'ELECTION_APPLICATION_DUPLICATE',
  'ELECTION_APPLICATION_SAVE_FAILED',
  'ELECTION_CANDIDATE_CREATE_FAILED',
  'ELECTION_BALLOT_INVALID',
  'ELECTION_BALLOT_ALREADY_USED',
  'ELECTION_VOTE_DUPLICATE',
  'ELECTION_VOTE_SAVE_FAILED',
  'ELECTION_STATION_DISABLED',
  'ELECTION_RESULTS_FAILED',
  'ELECTION_PRESIDENT_ASSIGN_FAILED',
  'ELECTION_SIDEBAR_RENDER_FAILED'
)) {
  Require-Text $code "Missing election error code: $code"
}

Require-Regex 'catch\(Exception ex\)[\s\S]*warn\(p,' 'Player-facing GUI failures must be caught and shown as a generic warning.'

foreach ($bad in @(
  "warn(p,`"SQLException",
  "warn(p,`"PSQLException",
  "warn(p,`"NullPointerException",
  "warn(p,`"ClassCastException",
  "warn(p,`"SELECT ",
  "warn(p,`"INSERT ",
  "warn(p,`"UPDATE ",
  "warn(p,`"DELETE ",
  "warn(p,`"cmv731_",
  "warn(p,`"cmv7_"
)) {
  if ($text.Contains($bad)) { $errors.Add("Player-facing warnings must not expose SQL/Java internals: $bad") }
}

if ($errors.Count -gt 0) {
  throw ("Election system validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election system validation passed: GUI, applications, ballots, stations, voting, president, sidebar and safe errors are wired.'
