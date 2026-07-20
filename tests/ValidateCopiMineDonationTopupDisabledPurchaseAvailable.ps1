. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$artifacts = Read-Utf8 $Paths.Artifacts
$artifactsConfig = Read-Utf8 $Paths.ArtifactsConfig
$donationPages = Read-Utf8 (Join-Path $root 'admin-web/frontend/assets/js/player/donation-pages.js')
$cartPage = Read-Utf8 (Join-Path $root 'admin-web/frontend/assets/js/public/cart-page.js')

Require-Contains $mainPy 'DONATION_TOPUP_ENABLED' 'Backend must expose an explicit donation top-up switch.'
Require-Contains $mainPy 'if not DONATION_TOPUP_ENABLED:' 'Player donation top-up must be blocked when the switch is disabled.'
Require-Contains $mainPy '"topupEnabled": DONATION_TOPUP_ENABLED' 'Donation balance API must publish top-up availability.'
Require-Contains $donationPages 'topupEnabled' 'Player donation UI must read the top-up availability flag.'
Require-Contains $donationPages 'DONATION_TOPUP_DISABLED_MESSAGE' 'Player donation UI must explain that top-up is disabled.'
Require-Contains $cartPage '/api/player/shop/cart/donation/checkout' 'Donation cart must keep its existing-balance checkout endpoint.'
Require-NotContains $donationPages 'DONATION_TOPUP_REQUIRED' 'Donation purchase UI must not require a top-up before an existing-balance purchase.'
Require-Contains $artifactsConfig 'topup-enabled: false' 'The game donation menu must default public top-up to disabled.'
Require-Contains $artifacts 'donationTopupEnabled()' 'The game donation menu must gate top-up actions without disabling purchases.'
Require-Contains $artifacts 'DonationPurchaseService' 'The game donation shop must keep the existing-balance purchase service.'

Throw-IfErrors 'ValidateCopiMineDonationTopupDisabledPurchaseAvailable'
