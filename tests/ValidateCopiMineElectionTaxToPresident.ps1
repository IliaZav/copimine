. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$artifacts = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))

Require-Contains $election 'activePresidentRevenueProfile()' 'ElectionCore must expose the active president revenue profile for shop payouts.'
Require-Contains $election 'artifact_revenue_payouts' 'ElectionCore payout history must read credited shop revenue instead of legacy tax payments.'
Require-Contains $election 'private void setPresidentTax(String actor, int amount) throws Exception {' 'ElectionCore must keep a guarded entry point for legacy callers.'
Require-Contains $election 'throw new IllegalStateException("\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.");' 'Legacy tax entry points must be hard-disabled.'

Require-Contains $artifacts 'resolveActivePresidentRevenueRecipient()' 'Artifacts shop must resolve the active president before persisting purchases.'
Require-Contains $artifacts 'artifact_revenue_payouts' 'Artifacts shop must persist dedicated president payout rows.'
Require-Contains $artifacts 'bridge.creditAccount(' 'Artifacts payout flow must credit the president through EconomyCore.'
Require-Contains $artifacts 'artifact-president-budget-' 'President payout credits must use an idempotency key.'

Throw-IfErrors 'ValidateCopiMineElectionTaxToPresident'
