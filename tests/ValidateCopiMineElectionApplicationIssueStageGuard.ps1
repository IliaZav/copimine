. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void issueApplicationBook'

Require-Contains $body 'STAGE_INDEPENDENT_ISSUE' 'issueApplicationBook() must document stage-independent issuance.'
Require-NotRegex $body 'context\.stage\(\)\s*!=\s*ElectionStage\.(PREPARATION|APPLICATIONS|REVIEW)' 'Issuing an application book must not be blocked by the current stage.'

Throw-IfErrors 'ValidateCopiMineElectionApplicationIssueStageGuard'
