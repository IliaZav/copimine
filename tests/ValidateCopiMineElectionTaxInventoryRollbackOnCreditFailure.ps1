. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$body = Method-Body $text 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

Require-Contains $body 'restoreInventorySnapshot(player, snapshot);' 'Inventory tax payments must restore inventory on credit failure.'
Require-Contains $body 'markTaxPaymentOperation(operationId, "FAILED"' 'Inventory tax payments must mark failures explicitly.'
Require-Contains $body 'markTaxPaymentOperation(operationId, "ECONOMY_CONFIRMED", result.txId, "");' 'Inventory tax payments must store bank tx proof before completion.'

Throw-IfErrors 'ValidateCopiMineElectionTaxInventoryRollbackOnCreditFailure'
