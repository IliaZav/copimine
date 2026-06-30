$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'ItemDisplay',
  'updateFloatingVisuals(Block cauldron, BlockKey key, List<IngredientEntry> ingredients)',
  'clearFloatingVisuals(Block block, BlockKey key)',
  'clearState(block, key, version)',
  'handleCauldronBroken(block, block.getLocation().add(0.5D, 0.5D, 0.5D))'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Floating ingredient lifecycle marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsFloatingIngredientsLifecycle passed.'
