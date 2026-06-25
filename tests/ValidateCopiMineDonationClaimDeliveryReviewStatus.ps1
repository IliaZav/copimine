. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy

Require-Contains $economy "status='DELIVERING'" 'EconomyCore donation claims must transition through DELIVERING before physical item handoff.'
Require-Contains $economy "status='DELIVERY_REVIEW'" 'EconomyCore donation claims must support DELIVERY_REVIEW for post-delivery reconciliation.'
Require-Contains $economy 'markClaimDeliveringAsync' 'EconomyCore DonationPurchaseService must expose markClaimDeliveringAsync.'
Require-Contains $economy 'markClaimDeliveryReviewAsync' 'EconomyCore DonationPurchaseService must expose markClaimDeliveryReviewAsync.'

Throw-IfErrors 'ValidateCopiMineDonationClaimDeliveryReviewStatus'
