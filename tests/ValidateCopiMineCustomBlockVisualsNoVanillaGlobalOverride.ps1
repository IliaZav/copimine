$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\models_manifest.json'
$srcRoot = Join-Path $root 'resourcepacks\src'
$errors = [System.Collections.Generic.List[string]]::new()

$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath
if ($manifest -notmatch '"base_material": "paper"') {
  $errors.Add('Block visuals must use paper CustomModelData entries, not vanilla block overrides.')
}

foreach ($forbidden in @(
  'assets\minecraft\textures\block',
  'assets\minecraft\models\block'
)) {
  if (Test-Path (Join-Path $srcRoot $forbidden)) {
    $errors.Add("Global vanilla block override path must not be created: $forbidden")
  }
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineCustomBlockVisualsNoVanillaGlobalOverride failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineCustomBlockVisualsNoVanillaGlobalOverride passed.'
