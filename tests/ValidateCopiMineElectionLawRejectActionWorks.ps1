. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'holder.rightActions().put' 'President admin law cards must wire right-click actions.'
Require-Contains $text '"law:reject:" + lawId' 'Pending law cards must expose reject action.'

Throw-IfErrors 'ValidateCopiMineElectionLawRejectActionWorks'
