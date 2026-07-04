$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 $source
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'pendingOfficialReturns' 'Official items removed from death drops must be queued for safe return.'
Require-Contains 'shouldPersistOfficialItem' 'Death protection must distinguish persistent official items from temporary books.'
Require-Contains 'restorePendingOfficialItems' 'Queued official items must be restored after respawn/join when slots are available.'
Require-Contains 'PlayerDeathEvent' 'Official custom items must not drop on player death.'
Require-Contains 'PlayerRespawnEvent' 'Official custom items must be restored after respawn.'
Require-Regex 'onJoin[\s\S]*restorePendingOfficialItems' 'Join must retry pending official item restoration.'
Require-Regex 'onOfficialItemDeath[\s\S]*getDrops[\s\S]*pendingOfficialReturns' 'Death handler must remove protected official items from drops and queue them.'

Require-Contains 'InventoryMoveItemEvent' 'Hopper-to-container movement of protected custom items must be blocked.'
Require-Contains 'BlockDispenseEvent' 'Dispensers and droppers must not eject protected custom items.'
Require-Contains 'PlayerInteractEntityEvent' 'Item frames and display entities must not accept protected custom items.'
Require-Contains 'PlayerArmorStandManipulateEvent' 'Armor stands must not accept protected custom items.'
Require-Regex 'onProtectedInventoryMove[\s\S]*isProtectedCustomItem[\s\S]*setCancelled\(true\)' 'InventoryMoveItemEvent must cancel protected custom item transfers.'
Require-Regex 'onProtectedBlockDispense[\s\S]*isProtectedCustomItem[\s\S]*setCancelled\(true\)' 'BlockDispenseEvent must cancel protected custom item dispensing.'
Require-Regex 'onProtectedEntityDisplay[\s\S]*isProtectedCustomItem[\s\S]*setCancelled\(true\)' 'Entity display placement must cancel protected custom item placement.'
Require-Regex 'onProtectedArmorStand[\s\S]*isProtectedCustomItem[\s\S]*setCancelled\(true\)' 'Armor stand manipulation must cancel protected custom item placement.'

Require-Contains 'runPreflightRepair' 'Preflight must expose a safe repair/prevention action.'
Require-Contains 'preflight:repair' 'Preflight UI must provide a repair action button.'
Require-Regex 'runPreflightRepair[\s\S]*purgeTemporaryApplicationBooks[\s\S]*restorePendingOfficialItems[\s\S]*reloadSidebarAll' 'Preflight repair must clean temp books, retry official item restores, and refresh sidebar.'

Require-Regex 'onInteract[\s\S]*isPollingStationBlock[\s\S]*depositSealedBallotAtStation[\s\S]*sendPollingStationCitizenInfo[\s\S]*openPollingStationHub' 'Polling station click must try ballot deposit first, then show citizen info, then open the station hub.'
Require-Regex 'giveRoleOfficialItemsAtStation[\s\S]*giveCikSealIfNeeded[\s\S]*givePresidentMandateIfNeeded' 'Station role auto-issue must cover both CIK seal and president mandate.'
Require-Regex 'private boolean isOfficialArItem\(ItemStack it\)[\s\S]*return isOfficialAr\(it\)\|\|isLegacyArItem\(it\);' 'Official AR detection must rely on plugin markers, not plain renamed text.'
Require-Regex 'deleteArPlacedBlock\(Block b\)[\s\S]*REMOVED_FROM_WORLD[\s\S]*AR_PLACED_BLOCK_REMOVED' 'Placed AR blocks must leave official circulation when the world block is removed.'
Require-Regex 'onOfficialArCreative\(InventoryCreativeEvent e\)[\s\S]*isOfficialArItem\(e\.getCursor\(\)\)[\s\S]*setCancelled\(true\)' 'Creative inventory must not duplicate official AR.'
if ([regex]::IsMatch($text, 'openPollingStationHub\(Player p,Block block\)(?:(?!private void sendPollingStationCitizenInfo)[\s\S])*official:recover', [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
  $errors.Add('Compact polling station GUI must not expose role recovery buttons to ordinary station flow.')
}

if ($errors.Count -gt 0) {
  throw ("Official item scenario guard validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Official item scenario guard validation passed.'
