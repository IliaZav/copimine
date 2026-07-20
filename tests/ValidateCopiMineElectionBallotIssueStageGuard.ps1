. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void issueBallot'

Require-Contains $body 'STAGE_INDEPENDENT_ISSUE' 'issueBallot() must document stage-independent issuance.'
Require-NotRegex $body 'context\.stage\(\)\s*!=\s*ElectionStage\.(DEBATES|VOTING|SECOND_ROUND)' 'Issuing a ballot must not be blocked by the current stage.'
$activeStatus = "status IN ('ISSUED','CONFIRMED','DEPOSITED')"
Require-Contains $body $activeStatus 'issueBallot() must still block duplicate active ballots.'

Throw-IfErrors 'ValidateCopiMineElectionBallotIssueStageGuard'
