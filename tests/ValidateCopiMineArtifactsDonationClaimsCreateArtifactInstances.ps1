. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'persistDonationInstances(' 'Artifacts donation claims must persist artifact_item_instances before delivery.'
Require-Contains $artifacts '"DELIVERING"' 'Artifacts donation flow must create provisional donation instance rows in DELIVERING status.'
Require-Contains $artifacts 'cacheOfficialBinding(uniqueItemId, itemId, readOwnerUuidForInstance(c, uniqueItemId));' 'Artifacts donation flow must register delivered donation instance ids in memory.'
Require-Contains $artifacts 'markDonationInstancesDelivered(' 'Artifacts donation flow must finalize provisional instance rows after physical delivery.'

Throw-IfErrors 'ValidateCopiMineArtifactsDonationClaimsCreateArtifactInstances'
