. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'resolveTaxPaymentEconomyProof' 'Legacy tax reconciliation helpers must still require EconomyCore proof if invoked during migration.'
Require-Contains $text 'economy_proof_missing' 'Legacy tax reconciliation helpers must stop when economy proof is missing.'
Require-NotContains $text 'completeTaxPaymentOperation(operationId, op == null ? "" : string(op.get("bank_tx_id")))' 'Reconcile must not auto-complete tax ops from stored bank_tx_id alone.'

Throw-IfErrors 'ValidateCopiMineElectionTaxNoFalseCompletedWithoutBankTx'
