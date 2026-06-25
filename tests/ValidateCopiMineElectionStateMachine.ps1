. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'private final ElectionStateMachine electionStateMachine' 'ElectionStateMachine must exist as a dedicated guard component.'
Require-Contains $text 'validateStageTransition' 'ElectionStateMachine must validate stage changes.'
Require-Contains $text 'countActiveRoundCandidates' 'State machine must check candidate count before opening voting.'
Require-Contains $text 'countActiveStations' 'State machine must check station count before opening voting.'
Require-Contains $text 'countTiedLeaders' 'State machine must check tied leaders before opening the second round.'

Throw-IfErrors 'ValidateCopiMineElectionStateMachine'
