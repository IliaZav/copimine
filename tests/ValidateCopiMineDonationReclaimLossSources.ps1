$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Require([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { throw $message }
}

Require 'reconcileDonationLossJournal();' 'The reclaim menu must reconcile the durable loss journal before querying rows.'
Require 'Instant.ofEpochMilli(var7.updatedAt())' 'Reclaim timestamps must use the millisecond database clock.'
Require 'recordDonationLossOnce(var4, "void")' 'Void-destroyed donation items must become reclaimable losses.'
Require 'var2.remove();' 'The void item entity must be removed only after its loss is journaled.'
Require 'case BLOCK_EXPLOSION:' 'Block explosions must be journaled for reclaim.'
Require 'case ENTITY_EXPLOSION:' 'Entity explosions must be journaled for reclaim.'
Require 'case CONTACT:' 'Cactus/contact losses must be journaled for reclaim.'
Require 'EntityRemoveEvent.Cause.PLUGIN' 'Plugin and creative cleanup removals must be journaled for reclaim.'
Require 'EntityRemoveEvent.Cause.DISCARD' 'Discard removals must be journaled for reclaim.'
Require 'EntityRemoveEvent.Cause.OUT_OF_WORLD' 'Out-of-world removals must be journaled for reclaim.'
Require 'InventoryCreativeEvent' 'Creative inventory deletion must be handled explicitly.'
Require 'handleCreativeDonationLoss' 'Creative cursor deletion must use the durable loss journal.'
$destroyHandler = [regex]::Match($source, '(?s)@EventHandler\(\s*priority\s*=\s*EventPriority\.(?<priority>[A-Z_]+),\s*ignoreCancelled\s*=\s*(?<ignore>true|false)\s*\)\s*public void onDonationItemDestroyed\(EntityDamageEvent')
if (-not $destroyHandler.Success) { throw 'Donation loss damage handler annotation was not found.' }
if ($destroyHandler.Groups['priority'].Value -ne 'MONITOR') { throw 'Loss damage handling must run at MONITOR after all protection listeners have made their final decision.' }
if ($destroyHandler.Groups['ignore'].Value -ne 'true') { throw 'Cancelled damage must never create a reclaim entry.' }

$destroyedBlock = [regex]::Match($source, '(?s)case VOID:.*?flushPendingDonationLossJournalAsync\(\);')
if (-not $destroyedBlock.Success) { throw 'Void loss handler was not found.' }
if ($destroyedBlock.Value.Contains('preserveDonationItemFromVoid')) {
  throw 'Void loss must not silently teleport the item away from the reclaim list.'
}

Write-Output 'Donation reclaim loss-source contract: PASS'
