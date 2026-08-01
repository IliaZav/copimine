$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Require([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { throw $message }
}

Require 'reconcileDonationLossJournal();' 'The reclaim menu must reconcile the durable loss journal before querying rows.'
Require 'journalPending' 'The reclaim menu must retry while a loss journal entry is waiting for its instance row.'
Require 'Instant.ofEpochMilli(var7.updatedAt())' 'Reclaim timestamps must use the millisecond database clock.'
Require 'DamageCause.VOID' 'Void-destroyed donation items must become reclaimable losses.'
Require 'var2.remove();' 'The damage item entity must be removed only after its loss is journaled.'
Require 'case KILL, WORLD_BORDER, CONTACT, ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE,' 'All Paper damage causes must be considered before an item is removed.'
Require 'EntityInsideBlockEvent' 'Cactus item removal must be observed at the block-contact boundary.'
Require 'onDonationItemInsideBlock(EntityInsideBlockEvent event)' 'Cactus item removal handler must exist.'
Require 'Material.CACTUS' 'Cactus item removal must explicitly recognize cactus blocks.'
Require 'recordDonationLossOnce(ref, "cactus")' 'Cactus item removal must use the idempotent loss journal.'
Require 'case DEATH, DESPAWN, DROP, ENTER_BLOCK, EXPLODE, HIT, MERGE, OUT_OF_WORLD, PLUGIN, DISCARD, TRANSFORMATION' 'All destructive entity-removal causes, including plugin/creative cleanup, transformation and merge, must be journaled for reclaim.'
Require 'case PICKUP, PLAYER_QUIT, UNLOAD' 'Pickup and unload causes must be excluded because the physical item is preserved.'
Require 'InventoryCreativeEvent' 'Creative inventory deletion must be handled explicitly.'
Require 'handleCreativeDonationLoss' 'Creative cursor deletion must use the durable loss journal.'
Require 'candidate = event.getCurrentItem();' 'Creative outside-window deletion must also handle clients that report the item in the clicked slot.'
Require 'player::updateInventory' 'Creative deletion must synchronize the cleared cursor back to the client.'
$destroyHandler = [regex]::Match($source, '(?s)@EventHandler\(\s*priority\s*=\s*EventPriority\.(?<priority>[A-Z_]+),\s*ignoreCancelled\s*=\s*(?<ignore>true|false)\s*\)\s*public void onDonationItemDestroyed\(EntityDamageEvent')
if (-not $destroyHandler.Success) { throw 'Donation loss damage handler annotation was not found.' }
if ($destroyHandler.Groups['priority'].Value -ne 'HIGHEST') { throw 'Loss damage handling must run at HIGHEST so the item is protected before vanilla damage removes it.' }
if ($destroyHandler.Groups['ignore'].Value -ne 'true') { throw 'Cancelled damage must never create a reclaim entry.' }

$destroyedBlock = [regex]::Match($source, '(?s)DamageCause\.VOID.*?flushPendingDonationLossJournalAsync\(\);')
if (-not $destroyedBlock.Success) { throw 'Void loss handler was not found.' }
if ($destroyedBlock.Value.Contains('preserveDonationItemFromVoid')) {
  throw 'Void loss must not silently teleport the item away from the reclaim list.'
}

Write-Output 'Donation reclaim loss-source contract: PASS'
