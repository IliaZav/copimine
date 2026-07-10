. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$body = Method-Body $text 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

Require-Contains $body 'throw new IllegalStateException(' 'Inventory tax entry point must remain disabled in favor of AR bank payments.'
Require-NotContains $body 'cmv4_bank_' 'Inventory tax entry point must not manipulate bank tables directly.'

Throw-IfErrors 'ValidateCopiMineElectionTaxInventoryRollbackOnCreditFailure'
