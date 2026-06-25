$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$entry = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\recipe\IngredientEntry.java')
$recipe = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\recipe\NarcoticsRecipeService.java')
foreach ($marker in @(
  'originalMaterial',
  'potionEffectId',
  'potionBaseType',
  'Material.matchMaterial(originalMaterial',
  'new ItemStack(material, Math.max(1, amount))',
  'setBasePotionType',
  'stack.getType().name()'
)) {
  if (($entry + $recipe) -notmatch [regex]::Escape($marker)) {
    throw "Potion drop preservation marker missing: $marker"
  }
}
Write-Host 'Potion ingredient drop preservation validation passed.'
