. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void issueApplicationBook'

Require-Contains $body 'ElectionStage.PREPARATION' 'issueApplicationBook() must allow PREPARATION when configured by workflow.'
Require-Contains $body 'ElectionStage.APPLICATIONS' 'issueApplicationBook() must allow APPLICATIONS.'
Require-NotContains $body 'ElectionStage.REVIEW' 'issueApplicationBook() must not allow REVIEW anymore.'

Throw-IfErrors 'ValidateCopiMineElectionApplicationIssueStageGuard'
