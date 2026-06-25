. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text "WHERE admin_status='PENDING' AND chair_recommendation='RECOMMEND'" 'Recommended applications must only show pending admin decisions.'
Require-Contains $text "WHERE admin_status='PENDING' AND chair_recommendation='NOT_RECOMMEND'" 'Not recommended applications must only show pending admin decisions.'
Require-Contains $text 'application:recommend:' 'Chair recommendation action is missing.'
Require-Contains $text 'application:no-recommend:' 'Chair negative recommendation action is missing.'

Throw-IfErrors 'ValidateCopiMineElectionApplicationFilters'
