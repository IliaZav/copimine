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
Require-Contains $artifacts 'bridge.creditAccount(' 'Artifacts payout flow must credit the president through EconomyCore.'
Require-Contains $artifacts 'artifact-president-budget-' 'President payout credits must use an idempotency key.'

Throw-IfErrors 'ValidateCopiMineElectionTaxToPresident'
