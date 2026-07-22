. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text "SELECT COUNT(*) FROM candidate_applications WHERE election_id=? AND status='SUBMITTED' AND admin_status='PENDING'" 'Only submitted applications still awaiting review may block the transition after debates.'
Require-Contains $text 'case DEBATES -> {' 'The state machine must explicitly guard the transition out of debates.'
Require-Contains $text 'if (to != ElectionStage.VOTING)' 'Debates must advance only to voting.'

Throw-IfErrors 'ValidateCopiMineElectionDebatesTransition'
