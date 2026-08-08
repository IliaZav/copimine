$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -Raw -Encoding UTF8 $sourcePath

function Require([string]$needle, [string]$message) {
  if (-not $source.Contains($needle)) { throw $message }
}

function Require-Regex([string]$pattern, [string]$message) {
  if ($source -notmatch $pattern) { throw $message }
}

# A durability break is an intentional terminal state, not a reclaimable loss.
$breakHandler = [regex]::Match($source, '(?s)public void onPlayerItemBreak\(PlayerItemBreakEvent var1\).*?(?=\n\s*@EventHandler|\n\s*private |\z)')
if (-not $breakHandler.Success) { throw 'PlayerItemBreakEvent handler was not found.' }
if ($breakHandler.Value -notmatch 'markDonationInstanceBroken') {
  throw 'A durability break must transition the durable item row to BROKEN.'
}
if ($breakHandler.Value -match 'recordDonationLossOnce') {
  throw 'A durability break must not enter the reclaimable loss journal.'
}

# Paper can remove an item entity because it merged into another entity; the
# unique PDC identity on the removed source must become reclaimable.
Require 'case DEATH, DESPAWN, DROP, ENTER_BLOCK, EXPLODE, HIT, MERGE, OUT_OF_WORLD, PLUGIN, DISCARD, TRANSFORMATION' 'Item-entity merge must be journaled as a reclaimable loss.'
Require 'ItemMergeEvent' 'Item merge must be observed before a unique PDC identity can disappear.'
Require 'onDonationItemMerge(ItemMergeEvent event)' 'Official item merges must be cancelled before the source entity is discarded.'
Require 'import org.bukkit.event.inventory.InventoryPickupItemEvent;' 'Inventory pickup events must be observed before a hopper stores the item.'
Require 'onInventoryPickupItem(InventoryPickupItemEvent event)' 'Official item container pickups must be cancelled.'
Require 'event.getItem().getItemStack()' 'Container pickup protection must inspect the picked item.'
Require 'import org.bukkit.event.block.BlockCookEvent;' 'Automatic furnace/campfire cooking must be guarded.'
Require 'import org.bukkit.event.inventory.BrewEvent;' 'Automatic brewing must be guarded.'
Require 'public void onOfficialBlockCook(BlockCookEvent event)' 'Official items must not be consumed by cooking.'
Require 'public void onOfficialBrew(BrewEvent event)' 'Official items must not be consumed by brewing.'
Require 'case CRAFTING, WORKBENCH, CRAFTER, FURNACE, BLAST_FURNACE, SMOKER, BREWING, SMITHING, ANVIL, GRINDSTONE, STONECUTTER, HOPPER, DROPPER, DISPENSER, LOOM, CARTOGRAPHY, ENCHANTING, MERCHANT, BEACON, COMPOSTER' 'Consumptive inventories must be protected.'

# Shop items are now ordinary items.  The old loss/reclaim handlers remain only
# as migration-readable code, but every live path is disabled by this boundary.
Require-Regex '(?s)private boolean customShopItemsAreVanilla\(\).*?return true;' 'The ordinary shop-item lifecycle boundary must be enabled.'
$moveHandler = [regex]::Match($source, '(?s)public void onInventoryMoveItem\(InventoryMoveItemEvent var1\).*?(?=\n\s*\/\*|\n\s*@EventHandler|\z)')
if (-not $moveHandler.Success -or $moveHandler.Value -notmatch 'Shop items intentionally follow vanilla hopper/container movement') {
  throw 'Hopper movement must follow vanilla behavior for ordinary shop items.'
}
if ($moveHandler.Value -match 'setCancelled\(') { throw 'Ordinary shop-item hopper movement must not be cancelled.' }

Write-Output 'Donation reclaim all-destruction-sources contract: PASS'
