. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-NotContains $artifacts 'Math.min(row.amount(), 27L)' 'Donation claim amount must not be silently truncated to 27.'
Require-Contains $artifacts 'requiredDonationSlots(var2.amount())' 'Donation claim flow must validate the full requested amount against inventory capacity.'
Require-Contains $artifacts 'freeStorageSlots(var1.getInventory()) < var6' 'Donation claim flow must compare full required slot count against actual free slots before completing the claim.'

Throw-IfErrors 'ValidateCopiMineDonationClaimAmountNoSilentTruncation'
