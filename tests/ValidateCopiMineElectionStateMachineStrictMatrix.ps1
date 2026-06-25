. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$stateMachine = Method-Body $election 'private final class ElectionStateMachine {'

if ($null -eq $stateMachine) {
  $errors.Add('ElectionStateMachine block not found.')
} else {
  Require-Contains $stateMachine 'case NONE -> to == ElectionStage.PREPARATION' 'ElectionStateMachine must allow only NONE -> PREPARATION.'
  Require-Contains $stateMachine 'case PREPARATION -> to == ElectionStage.APPLICATIONS' 'ElectionStateMachine must allow only PREPARATION -> APPLICATIONS.'
  Require-Contains $stateMachine 'case APPLICATIONS -> to == ElectionStage.REVIEW' 'ElectionStateMachine must allow only APPLICATIONS -> REVIEW.'
  Require-Contains $stateMachine 'if (to == ElectionStage.DEBATES)' 'ElectionStateMachine must gate REVIEW -> DEBATES.'
  Require-Contains $stateMachine 'if (to != ElectionStage.VOTING)' 'ElectionStateMachine must gate DEBATES -> VOTING.'
  Require-Contains $stateMachine 'case VOTING -> to == ElectionStage.COUNTING' 'ElectionStateMachine must allow only VOTING -> COUNTING.'
  Require-Contains $stateMachine 'if (to == ElectionStage.SECOND_ROUND)' 'ElectionStateMachine must gate COUNTING -> SECOND_ROUND.'
  Require-Contains $stateMachine 'case FINISHED -> to == ElectionStage.PRESIDENT_TERM' 'ElectionStateMachine must gate FINISHED -> PRESIDENT_TERM.'
  Require-Contains $stateMachine 'case PRESIDENT_TERM -> to == ElectionStage.FINISHED' 'ElectionStateMachine must allow PRESIDENT_TERM -> FINISHED only.'
}

Throw-IfErrors 'ValidateCopiMineElectionStateMachineStrictMatrix'
