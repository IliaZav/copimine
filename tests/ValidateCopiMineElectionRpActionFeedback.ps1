$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java')
$frontend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

function Get-Slice([string]$text, [string]$startNeedle, [string]$endNeedle) {
  $start = $text.IndexOf($startNeedle)
  if ($start -lt 0) { return '' }
  $end = $text.IndexOf($endNeedle, $start + $startNeedle.Length)
  if ($end -lt 0) { return $text.Substring($start) }
  return $text.Substring($start, $end - $start)
}

Require-Contains $source 'private void sendRpActionError' 'RP action errors must have a dedicated player-safe feedback path.'
Require-Regex $source 'private void runRpDatabaseAction[\s\S]*?sendRpActionError\(player, error,' 'Campaign/debate/voting actions must not turn expected state guards into generic bug reports.'
Require-Regex $source 'private void enqueueRpCandidateAction[\s\S]*?sendRpActionError\(player, cause' 'Candidate roster validation must not turn expected state guards into generic bug reports.'
$createBlock = Get-Slice $source 'private void createRpVotingBlockFromTargetAsync' 'private void createPollingStationFromTarget'
$disableBlock = Get-Slice $source 'private void disableRpVotingBlockAsync' 'private void finishRpElection'
Require-Contains $createBlock 'sendRpActionError(player, error,' 'Voting-block creation guards must not turn expected state into generic bug reports.'
Require-Contains $disableBlock 'sendRpActionError(player, error,' 'Voting-block removal guards must not turn expected state into generic bug reports.'
Require-Contains $source 'countActiveRoundCandidates' 'The debates prerequisite must remain checked.'
Require-Contains $source 'countActiveVotingBlocks' 'The voting-block prerequisite must remain checked.'
Require-Contains $source 'selected.size() < 2 || selected.size() > 4' 'The candidate-roster prerequisite must remain checked.'
Require-Contains $frontend 'buildRpElectionControlPayload' 'Web election actions must construct action-specific payloads.'
Require-Regex $frontend 'action === "stage"[\s\S]*?payload\.stage = String\(stage' 'Only a stage action may send a stage value.'
Require-Regex $frontend 'stage === "VOTING"[\s\S]*?payload\.voting_hours' 'Only voting-stage actions may send a voting duration.'
Require-Regex $frontend 'action === "finish"[\s\S]*?payload\.candidate_uuid' 'Only election completion may send a manual winner.'

if ($errors.Count -gt 0) {
  throw ("Election RP action feedback validation failed:`n - " + ($errors -join "`n - "))
}

Write-Output 'Election RP action feedback contract: PASS'
