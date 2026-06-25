$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$javaPath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$itemsPath = Join-Path $root 'copimine-artifacts\items.yml'
$manifestPath = Join-Path $root 'resourcepacks\models_manifest.json'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in @($javaPath,$itemsPath,$manifestPath)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing Zmei source file: $path") }
}

if (Test-Path $javaPath) {
  $java = Get-Content -Raw -Encoding UTF8 $javaPath
  foreach ($marker in @(
    '"zmei_gorynych"',
    '10001',
    'INVERTED_SCREEN',
    'ZMEI_GORYNYCH_POOP',
    'normalizeChance(parseInt(str(row.get("effect_chance_percent")), artifactEffectChance(itemId)))',
    'meta.setCustomModelData(item.customModelData())'
  )) {
    if ($java -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts Java Zmei marker missing: $marker") }
  }
}

if (Test-Path $itemsPath) {
  $items = Get-Content -Raw -Encoding UTF8 $itemsPath
  foreach ($marker in @(
    'id: zmei_gorynych',
    'custom_model_data: 10001',
    'effect_chance_percent: 10',
    'visual_effect_id: INVERTED_SCREEN'
  )) {
    if ($items -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts items.yml Zmei marker missing: $marker") }
  }
  if ($items -notmatch 'name:\s*"&6.+?"') {
    $errors.Add('Artifacts items.yml must keep a visible colored display name for zmei_gorynych.')
  }
}

if (Test-Path $manifestPath) {
  $manifest = Get-Content -Raw -Encoding UTF8 $manifestPath
  foreach ($marker in @('"id": "zmei_gorynych"','"custom_model_data": 10001','"base_material": "netherite_sword"')) {
    if ($manifest -notmatch [regex]::Escape($marker)) { $errors.Add("Resource pack manifest Zmei marker missing: $marker") }
  }
}

if ($errors.Count -gt 0) {
  throw ("Zmei Gorynych validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Zmei Gorynych validation passed: artifacts catalog, Java registry, and resource pack manifest are aligned.'
