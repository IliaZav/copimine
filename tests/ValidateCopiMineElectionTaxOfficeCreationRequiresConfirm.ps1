. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void handleMenuAction'

Require-Contains $body 'action.equals("tax:create-office")' 'Missing tax:create-office action.'
Require-Contains $body 'apply:tax:create-office' 'Tax office creation must use an apply action.'
Require-Contains $body 'openConfirmationMenu(player' 'Tax office creation must go through a confirmation menu.'

Throw-IfErrors 'ValidateCopiMineElectionTaxOfficeCreationRequiresConfirm'
