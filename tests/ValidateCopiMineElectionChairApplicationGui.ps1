. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'private void openChairApplicationsMenu' 'Chair station menu must open a dedicated station applications list.'
Require-Contains $text 'private void openChairApplicationDetail' 'Chair station menu must open a dedicated application detail card.'
Require-Contains $text 'chair:application:view:' 'Chair application menu must have per-application view actions.'
Require-Contains $text 'chair:application:recommend:' 'Chair application detail must support recommend action.'
Require-Contains $text 'chair:application:no-recommend:' 'Chair application detail must support reject recommendation action.'

Throw-IfErrors 'ValidateCopiMineElectionChairApplicationGui'
