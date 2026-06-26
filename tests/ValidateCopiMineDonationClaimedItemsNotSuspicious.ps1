. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts "status IN ('DELIVERED','ACTIVE')" 'Artifacts instance cache must trust only finalized official donation instances.'
Require-Contains $artifacts 'markDonationInstancesDelivered(itemId, uniqueItemIds);' 'Donation claim delivery must finalize DB status before the instance becomes trusted.'
Require-Contains $artifacts 'cacheOfficialBinding(uniqueItemId, itemId, readOwnerUuidForInstance(c, uniqueItemId));' 'Artifacts must still register finalized official instance ids in memory after delivery commits.'
Require-Contains $artifacts 'instanceToItem.containsKey(uniqueItemId)' 'Artifacts authenticity guard must continue to rely on registered instance ids.'

Throw-IfErrors 'ValidateCopiMineDonationClaimedItemsNotSuspicious'
