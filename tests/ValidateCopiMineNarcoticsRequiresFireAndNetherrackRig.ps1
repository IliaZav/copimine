$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
$supported = [regex]::Match($source, '(?s)public boolean isSupportedCauldron\(Block block\).*?(?=\r?\n\s*public boolean tryAddIngredient)')
$levelChange = [regex]::Match($source, '(?s)public void handleCauldronLevelChange\(Block block, BlockData newState\).*?(?=\r?\n\s*public int cachedStateCount)')
if (-not $supported.Success -or $supported.Value -notmatch 'hasBrewingRig') {
  throw 'Brewing support must require a fire-over-netherrack rig.'
}
if (-not $levelChange.Success -or $levelChange.Value -notmatch 'hasBrewingRig') {
  throw 'Water-level integrity checks must require the brewing rig.'
}
foreach ($marker in @('Material.FIRE','Material.SOUL_FIRE','Material.NETHERRACK')) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Brewing rig marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsRequiresFireAndNetherrackRig passed.'
