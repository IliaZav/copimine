. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election

Require-Contains $election 'validateStageTransition(connection, electionId, from, stage)' 'Stage changes must go through ElectionStateMachine.'
Require-Contains $election 'validateStageTransition(connection, electionId, from, ElectionStage.SECOND_ROUND)' 'Second round must go through ElectionStateMachine.'
Require-Contains $election 'private StageTransitionResult validateStageTransition(Connection connection, String electionId, ElectionStage from, ElectionStage to)' 'ElectionStateMachine facade must exist.'

Throw-IfErrors 'ValidateCopiMineElectionStateMachineActuallyUsed'
