. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$economy = Read-Utf8 $Paths.Economy

Require-Contains $artifacts 'reviewDonationClaimAsync(var1.getUniqueId(), var2.claimId())' 'Artifacts must send failed post-delivery claims to DELIVERY_REVIEW instead of auto-releasing them.'
Require-Contains $economy "status='DELIVERY_REVIEW'" 'EconomyCore must support DELIVERY_REVIEW status for ambiguous donation deliveries.'

Throw-IfErrors 'ValidateCopiMineDonationClaimNoAutoReleaseAfterPhysicalDelivery'
