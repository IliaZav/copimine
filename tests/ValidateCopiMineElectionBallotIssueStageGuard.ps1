. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void issueBallot'

Require-Contains $body 'ElectionStage.DEBATES' 'issueBallot() must allow DEBATES.'
Require-Contains $body 'ElectionStage.VOTING' 'issueBallot() must allow VOTING.'
Require-Contains $body 'hasApplicationInElection' 'issueBallot() must still block players who received application books.'
Require-Contains $body 'hasActiveBallot' 'issueBallot() must still block duplicate active ballots.'

Throw-IfErrors 'ValidateCopiMineElectionBallotIssueStageGuard'
