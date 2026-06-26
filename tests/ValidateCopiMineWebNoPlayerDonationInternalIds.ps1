. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$web = Read-Utf8 $Paths.MainPy
$frontend = Read-Utf8 $Paths.FrontendApp

Require-Contains $web 'item.pop("purchase_id", None)' 'Player-facing donation owned payload must hide purchase_id.'
Require-Contains $web 'item.pop("unique_item_id", None)' 'Player-facing donation owned payload must hide unique_item_id.'
Require-NotRegex $frontend 'loadPlayerDonationItems\(\).*?\{ key: "purchase_id"' 'Player donation items screen must not render internal purchase ids.'
Require-NotRegex $frontend 'loadPlayerDonationItems\(\).*?\{ key: "unique_item_id"' 'Player donation items screen must not render internal unique instance ids.'

Throw-IfErrors 'ValidateCopiMineWebNoPlayerDonationInternalIds'
