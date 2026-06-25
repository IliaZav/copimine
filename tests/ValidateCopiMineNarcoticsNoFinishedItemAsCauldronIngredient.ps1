$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$recipe = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\recipe\NarcoticsRecipeService.java')
$cauldron = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @(
  'itemFactory != null && itemFactory.isOfficialFinishedItem(stack)',
  'if (itemFactory.isOfficialFinishedItem(stack))',
  'return null;',
  'return false;'
)) {
  if (($recipe + $cauldron) -notmatch [regex]::Escape($marker)) {
    throw "Finished-item cauldron guard marker missing: $marker"
  }
}
Write-Host 'Finished narcotics are blocked from cauldron ingredient flow.'
