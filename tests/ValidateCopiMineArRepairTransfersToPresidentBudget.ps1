. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts '.transferToAccount(' 'AR repair flow must transfer AR through the economy bridge.'
Require-Contains $artifacts 'PRESIDENT_BUDGET_ACCOUNT_ID' 'AR repair flow must target the president treasury account.'
Require-Contains $artifacts '"AR_ITEM_REPAIR"' 'AR repair flow must use an explicit repair transaction type.'
Require-Contains $artifacts 'String repairIdempotencyKey = "artifact-repair-" + repairId' 'AR repair flow must use a stable repair-scoped idempotency key at the bridge boundary.'
Require-NotContains $artifacts '"artifact-repair-" + UUID.randomUUID()' 'AR repair flow must not create a second idempotency key that changes during a retry.'
Require-Contains $artifacts '"artifact-repair-refund-" + var7' 'AR repair flow must keep a refund path for persist failures.'
Require-Contains $artifacts '.transferFromAccount(' 'AR repair flow must support treasury-to-player refund on persist failure.'

Throw-IfErrors 'ValidateCopiMineArRepairTransfersToPresidentBudget'
