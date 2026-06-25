$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
foreach ($marker in @('Material.WATER_CAULDRON','Levelled','requireFullWater','tryAddIngredient')) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Cauldron brewing marker missing: $marker" }
}
foreach ($forbidden in @('special cauldron','protected brewing block','TextDisplay','ItemDisplay')) {
  if ($source -match [regex]::Escape($forbidden)) { throw "Forbidden special-cauldron marker found: $forbidden" }
}
Write-Host 'Vanilla cauldron brewing validation passed.'
