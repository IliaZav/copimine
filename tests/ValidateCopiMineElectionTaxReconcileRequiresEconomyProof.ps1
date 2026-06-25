. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$body = Method-Body $text 'private boolean reconcileTaxPaymentOperation(String operationId) throws Exception {'

Require-Contains $body 'resolveTaxPaymentEconomyProof' 'Reconcile must query EconomyCore proof.'
Require-Contains $body 'if (!proof.confirmed()) {' 'Reconcile must refuse completion without proof.'
Require-Contains $body "status='RECONCILE_REQUIRED'" 'Reconcile must keep unresolved tax ops in RECONCILE_REQUIRED.'
Require-Contains $body 'finalizeReconciledTaxPayment(operationId)' 'Reconcile must finalize only after proof exists.'

Throw-IfErrors 'ValidateCopiMineElectionTaxReconcileRequiresEconomyProof'
