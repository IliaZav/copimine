. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'persistDonationInstances(' 'Artifacts donation claims must persist artifact_item_instances before delivery.'
Require-Contains $artifacts "DONATION_DELIVERING" 'Artifacts donation flow must create provisional donation instance rows.'
Require-Contains $artifacts 'instanceToItem.put(uniqueItemId, itemId);' 'Artifacts donation flow must register delivered donation instance ids in memory.'
Require-Contains $artifacts 'markDonationInstancesDelivered(' 'Artifacts donation flow must promote provisional instance rows to DELIVERED.'

Throw-IfErrors 'ValidateCopiMineArtifactsDonationClaimsCreateArtifactInstances'
