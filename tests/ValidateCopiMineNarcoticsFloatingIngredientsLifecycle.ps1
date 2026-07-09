$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'MINIMUM_RECIPE_CHECK_SIZE = 3',
  'spawnQueuedParticles(block, frozen.size(), false)',
  'Particle.WITCH',
  'Particle.SMALL_FLAME',
  'clearState(block, key, version)',
  'handleCauldronBroken(block, block.getLocation().add(0.5D, 0.5D, 0.5D))'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Cauldron particle lifecycle marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsFloatingIngredientsLifecycle passed.'
