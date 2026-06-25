$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$recipe = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\recipe\NarcoticsRecipeService.java')
foreach ($marker in @('stack.getType() == Material.POTION', 'stack.getType() == Material.SPLASH_POTION', 'stack.getType() == Material.LINGERING_POTION')) {
  if ($recipe -notmatch [regex]::Escape($marker)) { throw "Potion variant marker missing: $marker" }
}
Write-Host 'Potion variants acceptance validation passed.'
