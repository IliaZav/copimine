$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'configService.items().get("zhuzevo")',
  'finishBrewing(block, key, zhuzevo, version, ingredientCount, true, initiator)',
  'if (wrongMix)',
  'Particle.EXPLOSION',
  'world.spawnParticle(Particle.SMOKE, center, 42'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Wrong-mix zhuzevo marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsWrongMixProducesZhuzevo passed.'
