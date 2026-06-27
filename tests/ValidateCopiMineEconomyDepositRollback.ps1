. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Economy

Require-Contains $text 'private String depositArFromHandAsync(Player player, String atmId, String accountScope) throws Exception {' 'EconomyCore must have async hand deposit wrapper.'
Require-Contains $text 'private String depositAllArAsync(Player player, String atmId, String accountScope) throws Exception {' 'EconomyCore must have async full inventory deposit wrapper.'
Require-Contains $text 'creditAsync(player.getUniqueId(), player.getName(),' 'Async deposit wrappers must use async bank credit.'
Require-Contains $text 'restoreInventorySnapshot(player, snapshot);' 'Full inventory deposit must restore inventory on failed credit.'

Throw-IfErrors 'ValidateCopiMineEconomyDepositRollback'
