. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$stateMachine = Method-Body $election 'private final class ElectionStateMachine {'

if ($null -eq $stateMachine) {
  $errors.Add('ElectionStateMachine block not found.')
} else {
  Require-Contains $stateMachine 'Map<String, Object> winnerRow = queryOne(connection, "SELECT manual_winner_uuid,president_uuid FROM elections WHERE id=? LIMIT 1", electionId);' 'State machine must check persisted winner fields from the database.'
  Require-Contains $stateMachine 'boolean hasWinner = winnerRow != null' 'State machine must derive whether a winner is confirmed.'
  Require-Contains $stateMachine 'if (to == ElectionStage.FINISHED || to == ElectionStage.PRESIDENT_TERM)' 'Counting stage must gate finish/president transitions.'
  Require-Contains $stateMachine 'hasWinner' 'Finish and president-term transitions must depend on a confirmed winner.'
  Require-Contains $stateMachine 'StageTransitionResult.deny(' 'State machine must deny finish/president-term transitions without a confirmed winner.'
}

Throw-IfErrors 'ValidateCopiMineElectionWinnerConfirmRequired'
