. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$persistBody = Method-Body $artifacts '    private void persistDonationInstances(UUID ownerUuid, String purchaseId, String itemId, List<String> uniqueItemIds) throws SQLException {'
$reclaimBody = Method-Body $artifacts '    private DonationReclaimContext prepareDonationReclaim(UUID ownerUuid, ReclaimableDonationRow row) throws SQLException {'
$cacheHelperBody = Method-Body $artifacts '    private void cacheOfficialBinding(String uniqueItemId, String itemId, String ownerUuid) {'

Require-Contains $artifacts "SELECT unique_item_id,item_id,owner_uuid FROM artifact_item_instances WHERE status IN ('DELIVERED','ACTIVE')" 'Artifacts instance cache must only trust delivered/active rows.'
if ($null -eq $persistBody) {
  $errors.Add('Artifacts validator could not locate persistDonationInstances body.')
} else {
  Require-NotContains $persistBody 'cacheOfficialBinding(' 'Artifacts must not cache donation instances while they are still DELIVERING.'
}
if ($null -eq $reclaimBody) {
  $errors.Add('Artifacts validator could not locate prepareDonationReclaim body.')
} else {
  Require-NotContains $reclaimBody 'cacheOfficialBinding(' 'Artifacts reclaim preparation must not trust replacement instances before final activation.'
}
if ($null -eq $cacheHelperBody) {
  $errors.Add('Artifacts validator could not locate cacheOfficialBinding helper.')
} else {
  Require-Contains $cacheHelperBody 'instanceToItem.put(safeUnique, safeItem);' 'Artifacts cache helper must still populate the trusted instance id registry.'
}
Require-Contains $artifacts 'cacheOfficialBinding(uniqueItemId, itemId, readOwnerUuidForInstance(c, uniqueItemId));' 'Artifacts must still repopulate cache after delivered/active transitions complete.'

Throw-IfErrors 'ValidateCopiMineArtifactsNoDeliveringCacheTrust'
