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

Require-Contains 'enum ElectionStage' 'Missing new election stage enum.'
Require-Contains 'openAdminElectionHub' 'Missing ElectionCore admin hub entrypoint.'
Require-Contains 'current_stage' 'Missing persisted current stage field.'
Require-Contains 'current_round' 'Missing persisted current round field.'
foreach ($status in @(
  'NONE',
  'PREPARATION',
  'APPLICATIONS',
  'REVIEW',
  'DEBATES',
  'VOTING',
  'COUNTING',
  'SECOND_ROUND',
  'FINISHED',
  'PRESIDENT_TERM'
)) {
  Require-Contains $status "Missing election status: $status"
}

Require-Contains 'issueApplicationBook' 'Missing candidate application issue flow.'
Require-Contains 'issueBallot' 'Missing ballot issue flow.'
Require-Contains 'depositBallot' 'Missing ballot deposit flow.'
Require-Contains 'countCurrentRound' 'Missing counting flow.'
Require-Contains 'startSecondRound' 'Missing second-round flow.'
Require-Contains 'assignPresident' 'Missing president assignment after results.'
Require-Contains 'hidelive' 'Missing /hidelive command support.'

foreach ($fragment in @(
  "stage='NOMINATION'",
  "stage='INAUGURATION'",
  'applications_open',
  'voting_open',
  'cmnarcotics market'
)) {
  if ($text.Contains($fragment)) {
    $errors.Add("Legacy flag/stage lifecycle fragment remains: $fragment")
  }
}

if ($errors.Count -gt 0) {
  throw ("Election lifecycle validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election lifecycle validation passed: ElectionCore stages, ballot/application flow, president assignment, and /hidelive are present.'
