$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'fire.getType() == Material.FIRE || fire.getType() == Material.SOUL_FIRE',
  'fuel.getType() == Material.NETHERRACK'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Brewing rig marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsRequiresFireAndNetherrackRig passed.'
