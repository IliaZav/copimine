$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifactsSource = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$artifactsItems = Join-Path $root 'copimine-artifacts\items.yml'
$serverItems = Join-Path $root 'minecraft\server\plugins\CopiMineArtifacts\items.yml'
$narcoticsSource = Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$narcoticsConfig = Join-Path $root 'copimine-narcotics\config.yml'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in @($artifactsSource,$artifactsItems,$serverItems,$narcoticsSource,$narcoticsConfig)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing release file: $path") }
}

if (Test-Path $artifactsItems) {
  $items = Get-Content -Raw -Encoding UTF8 $artifactsItems
  foreach ($marker in @(
    'id: zmei_gorynych',
    'category: WEAPON',
    'material: NETHERITE_SWORD',
    'custom_model_data: 10001',
    'effect: ZMEI_GORYNYCH_POOP',
    'effect_chance_percent: 10',
    'visual_effect_id: INVERTED_SCREEN'
  )) {
    if ($items -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts catalog missing marker: $marker") }
  }
  foreach ($forbidden in @(
    'dragon_punisher',
    'watch_blade',
    'silent_smuggler_bow',
    'category: RP',
    'black market'
  )) {
    if ($items -match [regex]::Escape($forbidden)) { $errors.Add("Artifacts catalog still contains retired marker: $forbidden") }
  }
}

if ((Test-Path $artifactsItems) -and (Test-Path $serverItems)) {
  $srcHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactsItems).Hash
  $serverHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverItems).Hash
  if ($srcHash -ne $serverHash) { $errors.Add('Server CopiMineArtifacts/items.yml is not synchronized with release catalog.') }
}

if (Test-Path $artifactsSource) {
  $java = Get-Content -Raw -Encoding UTF8 $artifactsSource
  foreach ($marker in @(
    'ARTIFACT_MODEL_DATA = Map.of(',
    '"zmei_gorynych", 10001',
    'ARTIFACT_EFFECT_CHANCE = Map.of(',
    '"zmei_gorynych", 10',
    'ARTIFACT_VISUAL_EFFECTS = Map.of(',
    '"zmei_gorynych", "INVERTED_SCREEN"',
    'custom_model_data',
    'effect_chance_percent',
    'visual_effect_id',
    'rollEffectChance',
    'meta.setCustomModelData(item.customModelData())',
    '"&cCancel"',
    '"&eClear"',
    '"&aEnter"',
    'VisualEffectService'
  )) {
    if ($java -notmatch [regex]::Escape($marker)) { $errors.Add("Artifacts source missing marker: $marker") }
  }
  foreach ($forbidden in @(
    'Bukkit.dispatchCommand(player, "cmnarcotics market")',
    'blackmarket:open',
    'blackMarketTabVisible',
    'retired-market:notice',
    'openBlackMarketTab',
    'pin:backspace',
    'category: RP'
  )) {
    if ($java -match [regex]::Escape($forbidden)) { $errors.Add("Artifacts source still contains retired marker: $forbidden") }
  }
}

if (Test-Path $narcoticsSource) {
  $java = Get-Content -Raw -Encoding UTF8 $narcoticsSource
  foreach ($forbidden in @('currentMarketStock','marketOpenOffset','stock-size','rotation')) {
    if ($java -match [regex]::Escape($forbidden)) { $errors.Add("Narcotics source still contains legacy market marker: $forbidden") }
  }
}

if (Test-Path $narcoticsConfig) {
  $cfg = Get-Content -Raw -Encoding UTF8 $narcoticsConfig
  foreach ($forbidden in @('cycle-days','open-days','stock-size','random-seed')) {
    if ($cfg -match [regex]::Escape($forbidden)) { $errors.Add("Narcotics config still contains market marker: $forbidden") }
  }
}

if ($errors.Count -gt 0) {
  throw ("Artifacts Phase 2 validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Artifacts Phase 2 validation passed: Zmei catalog active, black-market hooks removed, shared PIN pad aligned.'
