. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void handleMenuAction'

Require-Contains $body 'station:cleanup-labels:' 'Missing station label cleanup action.'
Require-Contains $body 'apply:station:cleanup-labels:' 'Station label cleanup must use an apply action.'
Require-Contains $body 'openConfirmationMenu(player' 'Station label cleanup must go through a confirmation menu.'

Throw-IfErrors 'ValidateCopiMineElectionTextDisplayCleanupRequiresConfirm'
