$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$items = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\items.yml')
$java = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()

function Get-ItemBlock([string]$id) {
    $pattern = '(?ms)^  - id: ' + [regex]::Escape($id) + '\s*$.*?(?=^  - id:|^donation-catalog:|\z)'
    $match = [regex]::Match($items, $pattern)
    if (-not $match.Success) { $errors.Add("Catalog item is missing: $id"); return '' }
    return $match.Value
}

function Require([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { $errors.Add($message) }
}

$repair = Get-ItemBlock 'repair_kit'
Require $repair 'material: SHEARS' 'repair_kit must use SHEARS.'
Require $repair 'price_ar: 10' 'repair_kit price must be 10 AR.'
Require $repair 'effect: REPAIR_KIT' 'repair_kit effect is missing.'
Require $repair 'custom_model_data: 10025' 'repair_kit custom model data is missing.'

$returnStone = Get-ItemBlock 'return_stone'
Require $returnStone 'material: ECHO_SHARD' 'return_stone must use ECHO_SHARD.'
Require $returnStone 'price_ar: 300' 'return_stone price must be 300 AR.'
Require $returnStone 'cooldown_seconds: 300' 'return_stone cooldown must be 300 seconds.'
Require $returnStone 'effect: RETURN_STONE' 'return_stone effect is missing.'
Require $returnStone 'custom_model_data: 10026' 'return_stone custom model data is missing.'

$torch = Get-ItemBlock 'infinite_torch'
Require $torch 'material: TORCH' 'infinite_torch must use TORCH.'
Require $torch 'price_ar: 100' 'infinite_torch price must be 100 AR.'
Require $torch 'effect: INFINITE_TORCH' 'infinite_torch effect is missing.'
Require $torch 'custom_model_data: 10027' 'infinite_torch custom model data is missing.'

foreach ($marker in @(
    'RepairKitMath.MAX_USES',
    'repairKitDamage.setMaxDamage',
    'setMaxStackSize(1)',
    'keyReturnStoneCooldownUntil',
    'returnStoneChannels',
    'getRespawnLocation()',
    'isSafeTeleportLocation',
    'restoreInfiniteTorchAfterSuccessfulPlacement',
    'event.canBuild()',
    'onInfiniteTorchMerge',
    'onInfiniteTorchCraft',
    'onInfiniteTorchAnvil',
    'onInfiniteTorchGrindstone',
    'onInfiniteTorchSmithing',
    'onInfiniteTorchInventoryMove',
    'onInfiniteTorchDispense',
    'InventoryCreativeEvent',
    'onUtilityArtifactCreative',
    'onReturnStoneDrop',
    'onReturnStoneClick',
    'onReturnStoneDrag',
    'cancelReturnStoneChannel'
)) {
    Require $java $marker "Implementation is missing marker: $marker"
}

if ($java.Contains('player.damage(6.0D)')) {
    $errors.Add('Explosive crossbow owner self-damage is still present.')
}

if ($errors.Count -gt 0) {
    throw ("Utility AR artifact validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Utility AR artifact validation passed.'
