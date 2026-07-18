$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$page = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\admin\narcotics-recipe-pages.js')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @(
  'class NarcoticsRecipesIn',
  'apply_mode',
  'minecraftItems',
  'NARCOTICS_RECIPE_APPLY_MODES',
  'run_systemctl',
  'cmnarcotics reload'
)) {
  if ($main -notmatch [regex]::Escape($marker)) { $errors.Add("Backend marker missing: $marker") }
}

foreach ($marker in @(
  'minecraftItems',
  "adminRecipeSave('save')",
  "adminRecipeSave('apply')",
  'recipe-slot-removable',
  'count >= 3'
)) {
  if ($page -notmatch [regex]::Escape($marker)) { $errors.Add("Frontend marker missing: $marker") }
}

if ($page -match 'const RECIPE_ITEM_TABS = \[' -and $page -notmatch 'itemCatalog') {
  $errors.Add('Recipe picker must use the complete server-provided item catalog instead of only a fixed short list.')
}

if ($errors.Count) { throw ("Narcotics recipe editor validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'CopiMine narcotics recipe editor contract OK'
