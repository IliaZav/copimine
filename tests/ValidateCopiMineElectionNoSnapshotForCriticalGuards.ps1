. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

foreach ($signature in @(
  'private void openBallotVoteMenu',
  'private void issueApplicationBook',
  'private void issueBallot',
  'private void confirmBallotChoice',
  'private void depositBallot'
)) {
  $body = Method-Body $text $signature
  Require-NotContains $body 'currentStage()' "$signature must not use snapshot-based currentStage() for critical guards."
  Require-NotContains $body 'snapshot.get()' "$signature must not use snapshot.get() for critical guards."
}

Throw-IfErrors 'ValidateCopiMineElectionNoSnapshotForCriticalGuards'
