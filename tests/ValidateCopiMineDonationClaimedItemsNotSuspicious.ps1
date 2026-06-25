. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts "status IN ('DELIVERED','PENDING_DELIVERY','DONATION_DELIVERING')" 'Artifacts instance cache must include donation-delivering items so claimed donation items stay official.'
Require-Contains $artifacts 'instanceToItem.containsKey(uniqueItemId)' 'Artifacts authenticity guard must continue to rely on registered instance ids.'

Throw-IfErrors 'ValidateCopiMineDonationClaimedItemsNotSuspicious'
