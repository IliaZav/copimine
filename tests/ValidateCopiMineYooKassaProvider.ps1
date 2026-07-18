. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"

$errors = New-ErrorList
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$mainPy = Read-Utf8 $Paths.MainPy
$envExample = Read-Utf8 (Join-Path $root 'admin-web\.env.example')
$playerDonation = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\player\donation-pages.js')
$gateway = Read-Utf8 (Join-Path $root 'admin-web\backend\yookassa_gateway.py')

Require-Contains $envExample 'YOOKASSA_ENABLED=0' 'Production env template must explicitly gate YooKassa.'
Require-Contains $envExample 'YOOKASSA_SHOP_ID=' 'Production env template must document YooKassa shop id.'
Require-Contains $envExample 'YOOKASSA_SECRET_KEY=' 'Production env template must document YooKassa secret key.'
Require-Contains $envExample 'YOOKASSA_RETURN_URL=' 'Production env template must document YooKassa return URL.'
Require-Contains $mainPy 'from .yookassa_gateway import YooKassaGateway' 'Donation backend must import the YooKassa gateway.'
Require-Contains $mainPy 'provider_payment_id' 'Donation sessions must persist the provider payment id.'
Require-NotContains $mainPy "provider_payment_id<>'')" 'Provider payment index SQL must not contain an extra closing parenthesis.'
Require-Contains $mainPy 'create_payment(' 'Donation sessions must create a provider payment when YooKassa is enabled.'
Require-Contains $mainPy 'verify_succeeded_payment(' 'YooKassa webhook must re-check provider state server-side.'
Require-Contains $mainPy '@app.post("/api/payments/yookassa/webhook")' 'Backend must expose a YooKassa webhook endpoint.'
Require-Contains $mainPy 'expected_provider_payment_id' 'Paid-state mutation must distinguish a verified provider payment from manual mock confirmation.'
Require-Contains $mainPy '"yookassa"' 'Verified YooKassa top-ups must be labeled in the immutable donation ledger.'
Require-NotContains $mainPy '"PAID" if payment.paid and payment.status == "succeeded" else "PENDING"' 'YooKassa creation must not mark a session paid before the shared idempotent balance mutation runs.'
Require-Contains $gateway '"Idempotence-Key"' 'YooKassa requests must be idempotent.'
Require-Contains $gateway 'metadata_mismatch' 'YooKassa verification must bind payments to a CopiMine session.'
Require-Contains $playerDonation 'confirmation_url' 'Donation screen must show the real provider checkout URL.'
Require-Contains $playerDonation 'provider === "YOOKASSA"' 'Donation screen must render a distinct YooKassa state.'

Throw-IfErrors 'ValidateCopiMineYooKassaProvider'
