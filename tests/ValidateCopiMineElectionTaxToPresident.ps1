. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$artifacts = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))

Require-Contains $election 'activePresidentRevenueProfile()' 'ElectionCore must expose the active president revenue profile for shop payouts.'
Require-Contains $election 'artifact_revenue_payouts' 'ElectionCore payout history must read credited shop revenue instead of legacy tax payments.'
Require-Contains $election 'private void setPresidentTax(String actor, int amount) throws Exception {' 'ElectionCore must keep a guarded entry point for legacy callers.'
Require-Contains $election 'private void setPresidentTax(String actor, int amount, int periodHours) throws Exception {' 'ElectionCore must support storing the selected tax period.'
Require-Contains $election 'president_tax_payments' 'ElectionCore payout history must include voluntary president tax payments.'
Require-Contains $election 'PRESIDENT_BUDGET' 'President tax payments must credit the dedicated president budget account.'

Require-Contains $artifacts 'resolveActivePresidentRevenueRecipient()' 'Artifacts shop must resolve the active president before persisting purchases.'
Require-Contains $artifacts 'artifact_revenue_payouts' 'Artifacts shop must persist dedicated president payout rows.'
Require-Contains $artifacts 'ArtifactRevenuePayoutPolicy.pendingRowAction(status, bankTransactionId)' 'Artifacts payout reconciliation must inspect the already-committed bank transfer.'
Require-Contains $artifacts 'this.markRevenuePayoutCredited(var1, bankTransactionId)' 'Legacy pending payout rows must be reconciled using their existing transfer id.'
$workerStart = $artifacts.IndexOf('private void processRevenuePayout(String var1) throws Exception {', [StringComparison]::Ordinal)
$workerEnd = if ($workerStart -ge 0) { $artifacts.IndexOf('private void markRevenuePayoutCredited(', $workerStart, [StringComparison]::Ordinal) } else { -1 }
Require-Contains $artifacts 'String reason = amount <= 0L ? "amount_invalid" : "bank_tx_missing";' 'Invalid or missing payout data must be assigned the correct manual-review reason.'
Require-Contains $artifacts 'this.markRevenuePayoutReview(var1, reason)' 'Payout rows without a bank transfer id must fail closed for manual review.'
if ($workerStart -lt 0 -or $workerEnd -le $workerStart) {
    $errors.Add('Could not isolate the artifact payout worker for its no-double-credit check.')
} elseif ($artifacts.Substring($workerStart, $workerEnd - $workerStart) -match '\bcreditAccount\s*\(') {
    $errors.Add('The artifact payout worker must not issue a second credit after a transferToAccount purchase.')
}
Require-Contains $artifacts 'artifact-president-budget-' 'President payout credits must use an idempotency key.'

Throw-IfErrors 'ValidateCopiMineElectionTaxToPresident'
