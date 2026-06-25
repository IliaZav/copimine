. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'ECONOMY_STARTED' 'Tax payment ops must track ECONOMY_STARTED.'
Require-Contains $text 'ECONOMY_CONFIRMED' 'Tax payment ops must track ECONOMY_CONFIRMED.'
Require-Contains $text 'resolveTaxPaymentEconomyProof' 'Tax completion must require EconomyCore proof.'
Require-Contains $text 'economy_proof_missing' 'Tax completion must stop when economy proof is missing.'
Require-NotContains $text 'completeTaxPaymentOperation(operationId, op == null ? "" : string(op.get("bank_tx_id")))' 'Reconcile must not auto-complete tax ops from stored bank_tx_id alone.'

Throw-IfErrors 'ValidateCopiMineElectionTaxNoFalseCompletedWithoutBankTx'
