$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'configService.items().get("zhuzevo")',
  'finishWrongMix(block, key, nextVersion, current.size())',
  'finishBrewing(block, key, zhuzevo, version, ingredientCount, true)',
  'Particle.EXPLOSION',
  'damage(6.0D)'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Wrong-mix zhuzevo marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsWrongMixProducesZhuzevo passed.'
