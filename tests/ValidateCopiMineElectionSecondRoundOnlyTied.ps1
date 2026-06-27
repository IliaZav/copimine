. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$stateMachine = Method-Body $election 'private final class ElectionStateMachine {'

if ($null -eq $stateMachine) {
  $errors.Add('ElectionStateMachine block not found.')
} else {
  Require-Contains $stateMachine 'long tiedLeaders = countTiedLeaders(connection, electionId, round);' 'State machine must calculate tied leaders from the current round.'
  Require-Contains $stateMachine 'if (to == ElectionStage.SECOND_ROUND)' 'Counting stage must explicitly gate entry into SECOND_ROUND.'
  Require-Contains $stateMachine 'tiedLeaders >= 2' 'Second round must require at least two tied leaders.'
  Require-Contains $stateMachine 'case SECOND_ROUND -> {' 'Second-round state must have dedicated transition rules.'
  Require-Contains $stateMachine 'if (tiedLeaders < 2) {' 'Second-round state must reject transitions when a tie no longer exists.'
}

Throw-IfErrors 'ValidateCopiMineElectionSecondRoundOnlyTied'
