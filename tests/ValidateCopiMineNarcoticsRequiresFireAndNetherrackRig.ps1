$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
$supported = [regex]::Match($source, '(?s)public boolean isSupportedCauldron\(Block block\).*?(?=\r?\n\s*public boolean tryAddIngredient)')
$levelChange = [regex]::Match($source, '(?s)public void handleCauldronLevelChange\(Block block, BlockData newState\).*?(?=\r?\n\s*public int cachedStateCount)')
if (-not $supported.Success -or $supported.Value -match 'hasBrewingRig') {
  throw 'Brewing support must accept a full vanilla WATER_CAULDRON without a fire/netherrack rig.'
}
if (-not $levelChange.Success -or $levelChange.Value -match 'hasBrewingRig') {
  throw 'Water-level integrity checks must not invalidate a vanilla cauldron because it lacks a rig.'
}
Write-Host 'ValidateCopiMineNarcoticsRequiresFireAndNetherrackRig passed (legacy rig is optional).'
