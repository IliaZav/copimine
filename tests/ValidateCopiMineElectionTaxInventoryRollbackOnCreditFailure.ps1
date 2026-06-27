. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$body = Method-Body $text 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

Require-Contains $body '\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.' 'Inventory tax entry point must be hard-disabled.'
Require-NotContains $body 'cmv4_bank_' 'Disabled tax entry point must not manipulate bank tables directly.'

Throw-IfErrors 'ValidateCopiMineElectionTaxInventoryRollbackOnCreditFailure'
