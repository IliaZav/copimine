$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'extinguishRig(block);',
  'block.setType(Material.CAULDRON, false);'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Brew cleanup marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsBrewClearsWaterAndFire passed.'
