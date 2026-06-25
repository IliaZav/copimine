$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$factory = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java')
foreach ($marker in @('inventory.setItem(index, createOfficialItem(', 'player.getInventory().setItemInOffHand(', 'player.getEnderChest()', 'migrateStorageInventory')) {
  if ($factory -notmatch [regex]::Escape($marker)) { throw "Texture migration slot-write marker missing: $marker" }
}
Write-Host 'Texture migration writes real inventory slots.'
