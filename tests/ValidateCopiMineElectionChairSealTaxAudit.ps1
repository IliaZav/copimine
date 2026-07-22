$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

$claim = [regex]::Match($source, '(?s)private void claimChairForStation\(Player player, String stationId\) throws Exception \{.*?\n    \}')
if (-not $claim.Success -or $claim.Value -notmatch 'requireStationForElection\(stationId, requireActiveElectionId\(\)\)') {
  throw 'Self-chair claim must be bound to the active election, not only to an active block.'
}

$drop = [regex]::Match($source, '(?s)public void onDrop\(PlayerDropItemEvent event\) \{.*?\n    \}')
if (-not $drop.Success -or $drop.Value -match 'if \(isCikSeal\(dropped\)\) \{\s*event\.setCancelled\(true\)') {
  throw 'Dropping a CIK seal must not cancel the drop, otherwise Minecraft returns it to the inventory.'
}
if (-not $drop.Success -or $drop.Value -notmatch 'event\.getItemDrop\(\)\.remove\(\)') {
  throw 'Dropping a CIK seal must remove the spawned item entity.'
}

$destroy = [regex]::Match($source, '(?s)private void destroyOneCikSeal\(Player player, String sealId\) \{.*?\n    \}')
if (-not $destroy.Success -or $destroy.Value -notmatch 'seal_destroyed') {
  throw 'Seal destruction must revoke the exact seal in the database.'
}
if (-not $destroy.Success -or $destroy.Value -notmatch 'removedFromInventory') {
  throw 'Seal destruction must persist even after PlayerDropEvent has already removed the inventory slot.'
}
if (-not $destroy.Success -or $destroy.Value -notmatch 'player_uuid') {
  throw 'Seal destruction must be bound to the player who owns the dropped seal.'
}

if ($source -notmatch 'private Map<String, Object> requireActiveTaxRecord\(String taxId\)') {
  throw 'Tax payment must validate the requested tax against the active presidential term.'
}
if ($source -notmatch 'if \(!isPresident\(player\)\)') {
  throw 'Manual tax-paid marking must remain president-only.'
}
if ($source -notmatch 'taxMinAmount\(\).*taxMaxAmount\(\)') {
  throw 'Tax amount must be bounded by the configured 0..5 range.'
}
Write-Host 'Election chair, seal and tax regression contract passed.'
