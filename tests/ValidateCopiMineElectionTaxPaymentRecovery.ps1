$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

$payment = [regex]::Match($source, '(?s)private void payTaxFromBank\(Player player, String taxId, String pin\) throws Exception \{.*?(?=\r?\n\s*private CopiMineEconomyCore\.BankService requireEconomyBankService)')
if (-not $payment.Success -or
    $payment.Value -notmatch 'try \{\s*completeTaxPaymentOperation\(operationId, result\.txId\)' -or
    $payment.Value -notmatch 'markTaxPaymentOperation\(operationId, "RECONCILE_REQUIRED", result\.txId') {
    throw 'A post-transfer finalization failure must leave the tax payment recoverable instead of losing it.'
}

$reconcile = [regex]::Match($source, '(?s)private void reconcilePendingTaxPaymentsSafe\(\) \{.*?(?=\r?\n\s*private boolean finalizeReconciledTaxPayment)')
if (-not $reconcile.Success -or
    $reconcile.Value -notmatch "status IN \('PENDING','ECONOMY_CONFIRMED','RECONCILE_REQUIRED'\)") {
    throw 'Startup reconciliation must inspect every non-final tax payment status.'
}

$completed = [regex]::Match($source, '(?s)private boolean isTaxPaymentOperationCompleted\(String operationId\) throws Exception \{.*?(?=\r?\n\s*private )')
if (-not $completed.Success -or $completed.Value -notmatch 'row != null' -or $completed.Value -notmatch 'row\.get\("status"\)') {
    throw 'A missing tax operation must be treated as incomplete instead of causing a null dereference.'
}

Write-Host 'Election tax payment recovery contract OK'
