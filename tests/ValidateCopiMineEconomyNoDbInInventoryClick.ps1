. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Economy
$body = Method-Body $text 'private void handleMenuAction(Player player, MenuHolder menu, String action) throws Exception {'

Require-Contains $body 'createBankAtmFromTargetAsync(player)' 'ATM create must use async wrapper from InventoryClick.'
Require-Contains $body 'archiveBankAtmAsync(player, action.substring("atm:delete:".length()))' 'ATM archive must use async wrapper from InventoryClick.'
Require-Contains $body 'depositArFromHandAsync(player, action.substring("atm:deposit-hand:".length()))' 'ATM deposit from hand must use async wrapper from InventoryClick.'
Require-Contains $body 'depositAllArAsync(player, action.substring("atm:deposit-all:".length()))' 'ATM deposit all must use async wrapper from InventoryClick.'

Throw-IfErrors 'ValidateCopiMineEconomyNoDbInInventoryClick'
