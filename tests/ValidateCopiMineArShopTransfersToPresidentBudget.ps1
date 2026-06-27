. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts '.transferToAccount(' 'AR purchase flow must transfer AR through the economy bridge.'
Require-Contains $artifacts 'PRESIDENT_BUDGET_ACCOUNT_ID' 'AR purchase flow must target the president treasury account.'
Require-Contains $artifacts '"AR_SHOP_PURCHASE"' 'AR purchase flow must use an explicit purchase transaction type.'
Require-Contains $artifacts '"artifact-purchase-" + var6' 'AR purchase flow must use purchase-scoped idempotency keys at the bridge boundary.'
Require-Contains $artifacts 'artifact-president-budget-' 'AR purchase persistence must keep budget credit rows traceable.'
Require-Contains $artifacts '.transferFromAccount(' 'AR purchase flow must keep the refund path for persist failures.'

Throw-IfErrors 'ValidateCopiMineArShopTransfersToPresidentBudget'
