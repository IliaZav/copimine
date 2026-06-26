. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $economy "UPDATE donation_item_claims SET status='RESERVED'" 'EconomyCore must transition donation claims to RESERVED.'
Require-Contains $economy "UPDATE donation_item_claims SET status='DELIVERING'" 'EconomyCore must transition donation claims to DELIVERING.'
Require-Contains $economy "UPDATE donation_item_claims SET status='CLAIMED'" 'EconomyCore must transition donation claims to CLAIMED.'
Require-Contains $economy "UPDATE donation_item_claims SET status='DELIVERY_REVIEW'" 'EconomyCore must support DELIVERY_REVIEW for donation claims.'
Require-Contains $economy "UPDATE donation_item_claims SET status='UNCLAIMED'" 'EconomyCore must support releasing donation claims back to UNCLAIMED.'
Require-Contains $artifacts 'reserveDonationClaimAsync' 'Artifacts must reserve donation claims before physical issuance.'
Require-Contains $artifacts 'markDonationClaimDeliveringAsync' 'Artifacts must mark donation claims DELIVERING before physical issuance.'
Require-Contains $artifacts 'completeDonationClaimAsync' 'Artifacts must complete donation claims after physical issuance.'
Require-Contains $artifacts 'reviewDonationClaimAsync' 'Artifacts must move broken donation deliveries into DELIVERY_REVIEW.'
Require-Contains $artifacts 'releaseDonationClaimAsync' 'Artifacts must release donation claims only before physical issuance.'

Throw-IfErrors 'ValidateCopiMineDonationClaimLifecycleStrict'
