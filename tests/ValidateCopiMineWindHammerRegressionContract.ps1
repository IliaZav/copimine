$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$contractPath = Join-Path $root 'admin-web\scripts\regression_contract_test.py'
$itemsPath = Join-Path $root 'copimine-artifacts\items.yml'
$sourcePath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$modelRoot = Join-Path $root 'resourcepacks\src\assets\copimine\models\item\artifacts'
$textureRoot = Join-Path $root 'resourcepacks\src\assets\copimine\textures\item\artifacts'

$contract = Get-Content -Raw -Encoding UTF8 $contractPath
$items = Get-Content -Raw -Encoding UTF8 $itemsPath
$source = Get-Content -Raw -Encoding UTF8 $sourcePath
$windHammerName = '&6' + [string][char]0x041C + [char]0x043E + [char]0x043B + [char]0x043E + [char]0x0442 + ' ' + [char]0x0412 + [char]0x0435 + [char]0x0442 + [char]0x0440 + [char]0x0430
$windHammerLore = [string][char]0x041F + [char]0x041A + [char]0x041C + ' ' + [char]0x043F + [char]0x043E + ' ' + [char]0x0437 + [char]0x0435 + [char]0x043C + [char]0x043B + [char]0x0435 + ': ' + [char]0x0443 + [char]0x0434 + [char]0x0430 + [char]0x0440 + ' ' + [char]0x0432 + [char]0x0435 + [char]0x0442 + [char]0x0440 + [char]0x0430 + '.'
$aceLore = [string][char]0x0437 + [char]0x0430 + ' ' + [char]0x043F + [char]0x043E + [char]0x043C + [char]0x043E + [char]0x0449 + [char]0x044C + ' ' + [char]0x0432 + ' ' + [char]0x0440 + [char]0x0430 + [char]0x0437 + [char]0x0432 + [char]0x0438 + [char]0x0442 + [char]0x0438 + [char]0x0438 + ' ' + [char]0x043F + [char]0x0440 + [char]0x043E + [char]0x0435 + [char]0x043A + [char]0x0442 + [char]0x0430 + ' CopiMine'
$staleAirLore = [string][char]0x041F + [char]0x041A + [char]0x041C + ' ' + [char]0x0432 + ' ' + [char]0x0432 + [char]0x043E + [char]0x0437 + [char]0x0434 + [char]0x0443 + [char]0x0445 + [char]0x003A + ' ' + [char]0x0421 + [char]0x043F + [char]0x0435 + [char]0x0448 + [char]0x043A + [char]0x0430 + ' II ' + [char]0x043D + [char]0x0430 + ' 3 ' + [char]0x043C + [char]0x0438 + [char]0x043D + [char]0x0443 + [char]0x0442 + [char]0x044B + '.'
$staleCooldownLore = [string][char]0x041F + [char]0x0435 + [char]0x0440 + [char]0x0435 + [char]0x0437 + [char]0x0430 + [char]0x0440 + [char]0x044F + [char]0x0434 + [char]0x043A + [char]0x0430 + [char]0x003A + ' 20 ' + [char]0x0441 + [char]0x0435 + [char]0x043A + [char]0x0443 + [char]0x043D + [char]0x0434 + '.'

foreach ($marker in @(
  ('name: "' + $windHammerName + '"'),
  'effect: WIND_HAMMER',
  'cooldown_seconds: 60',
  $windHammerLore,
  'getNearbyEntities(center, 10.0D, 10.0D, 10.0D)',
  'PotionEffectType.LEVITATION, 80, 0',
  'source: ADMIN_ONLY',
  $aceLore,
  'adminOnlyCatalogItems'
)) {
  if ($contract -notmatch [regex]::Escape($marker)) {
    throw "Regression contract no longer checks required item behavior: $marker"
  }
}

foreach ($staleMarker in @(
  'Craftsman Hammer implementation',
  'effect: HASTE_BURST_LONG',
  $staleAirLore,
  $staleCooldownLore
)) {
  if ($contract -match [regex]::Escape($staleMarker)) {
    throw "Regression contract still contains stale Craftsman Hammer expectation: $staleMarker"
  }
}

$hammer = [regex]::Match($items, '(?ms)^  - id: craftsman_hammer\r?\n.*?(?=^  - id:|\z)')
if (-not $hammer.Success) { throw 'Wind hammer catalog entry is missing.' }
foreach ($marker in @('material: MACE', 'custom_model_data: 10012', 'effect: WIND_HAMMER', 'cooldown_seconds: 60')) {
  if ($hammer.Value -notmatch [regex]::Escape($marker)) { throw "Wind hammer catalog marker is missing: $marker" }
}

$windAbility = [regex]::Match($source, '(?s)private boolean triggerWindHammer\(Player player, Block ground\) \{.*?(?=\r?\n\s*private )')
if (-not $windAbility.Success) { throw 'Wind hammer ability implementation is missing.' }
foreach ($marker in @(
  'getNearbyEntities(center, 10.0D, 10.0D, 10.0D)',
  'distanceSquared(center) > 100.0D',
  'entity == player',
  'setY(Math.max(living.getVelocity().getY(), 1.15D))',
  'PotionEffectType.LEVITATION, 80, 0'
)) {
  if ($windAbility.Value -notmatch [regex]::Escape($marker)) { throw "Wind hammer ability marker is missing: $marker" }
}

foreach ($itemId in @('craftsman_hammer', 'kozyrny_tuz_pozdnyakova')) {
  $model = Join-Path $modelRoot "$itemId.json"
  $texture = Join-Path $textureRoot "$itemId.png"
  if (-not (Test-Path -LiteralPath $model)) { throw "Missing resource-pack model for $itemId" }
  if (-not (Test-Path -LiteralPath $texture)) { throw "Missing resource-pack texture for $itemId" }
  if ((Get-Item -LiteralPath $texture).Length -le 0) { throw "Resource-pack texture is empty for $itemId" }
  $modelText = Get-Content -Raw -Encoding UTF8 $model
  if ($modelText -notmatch [regex]::Escape("copimine:item/artifacts/$itemId")) {
    throw "Resource-pack model does not point to its item texture: $itemId"
  }
}

& python $contractPath
if ($LASTEXITCODE -ne 0) { throw 'Updated artifact regression contract failed.' }

Write-Host 'Wind hammer regression contract and item resource checks passed.'
