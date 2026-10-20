. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts '.transferToAccount(' 'AR purchase flow must transfer AR through the economy bridge.'
Require-Contains $artifacts 'PRESIDENT_BUDGET_ACCOUNT_ID' 'AR purchase flow must target the president treasury account.'
Require-Contains $artifacts '"AR_SHOP_PURCHASE"' 'AR purchase flow must use an explicit purchase transaction type.'
Require-Contains $artifacts '"artifact-purchase-" + var6' 'AR purchase flow must use purchase-scoped idempotency keys at the bridge boundary.'
Require-Contains $artifacts 'artifact-president-budget-' 'AR purchase persistence must keep budget credit rows traceable.'
Require-Contains $artifacts 'ArtifactRevenuePayoutPolicy.shouldRefundAfterPersistenceFailure(var15.getMessage())' 'Only definitive pre-commit limit failures may use an immediate AR refund.'
Require-Contains $artifacts '.transferFromAccount(' 'AR purchase flow must keep an idempotent refund path for definitive limit rejection.'
Require-Contains $artifacts 'purchase_reconciliation_pending' 'Ambiguous persistence failures must wait for orphan reconciliation instead of refunding immediately.'

Throw-IfErrors 'ValidateCopiMineArShopTransfersToPresidentBudget'
