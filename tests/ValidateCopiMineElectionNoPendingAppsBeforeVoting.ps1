. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$stateMachine = Method-Body $election 'private final class ElectionStateMachine {'

if ($null -eq $stateMachine) {
  $errors.Add('ElectionStateMachine block not found.')
} else {
  Require-Contains $stateMachine 'long pendingApplications = scalarLong(connection, "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND status=''SUBMITTED'' AND admin_status=''PENDING''"' 'State machine must count only submitted pending candidate applications from DB.'
  Require-Contains $stateMachine 'if (pendingApplications > 0) {' 'State machine must explicitly guard transitions when pending applications remain.'
  Require-Contains $stateMachine 'case REVIEW -> {' 'Review stage must be validated before debates.'
  Require-Contains $stateMachine 'case DEBATES -> {' 'Debates stage must be validated before voting.'
  Require-Contains $stateMachine 'yield StageTransitionResult.deny(' 'State machine must deny debates or voting while pending applications remain.'
}

Throw-IfErrors 'ValidateCopiMineElectionNoPendingAppsBeforeVoting'
