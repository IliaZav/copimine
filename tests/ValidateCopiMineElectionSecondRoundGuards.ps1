$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$election = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java')
$errors = New-Object System.Collections.Generic.List[string]

$stateStart = $election.IndexOf('private final class ElectionStateMachine {')
$stateEnd = $election.IndexOf('private record CandidateResult', [Math]::Max(0, $stateStart))
if ($stateStart -lt 0 -or $stateEnd -lt $stateStart) {
  $errors.Add('Election state machine body could not be located.')
} else {
  $body = $election.Substring($stateStart, $stateEnd - $stateStart)
  if ($body -notmatch '(?s)case SECOND_ROUND.*?to == ElectionStage\.VOTING.*?activeCandidates < 2') {
    $errors.Add('Second-round voting transition must require at least two active candidates.')
  }
  if ($body -notmatch '(?s)case SECOND_ROUND.*?to == ElectionStage\.VOTING.*?stations < 1') {
    $errors.Add('Second-round voting transition must require an active polling station.')
  }
}

if ($errors.Count -gt 0) {
  throw ("Election second-round validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election second-round validation passed.'
