. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'station:assign-chair:' 'Station chair picker actions are missing.'
Require-Contains $text 'chair:ballots:' 'Chair ballot paging actions are missing.'
Require-Contains $text 'president:payments:' 'President payments paging action is missing.'
Require-Contains $text 'action.startsWith("station:assign-chair:") && action.contains(":page:")' 'Station chair paging must be parsed before generic station routing.'
Require-Contains $text 'action.startsWith("chair:ballots:")' 'Chair ballots action parser must exist.'
Require-Contains $text 'action.startsWith("president:payments:")' 'President payments action parser must exist.'

Throw-IfErrors 'ValidateCopiMineElectionGuiDeadActions'
