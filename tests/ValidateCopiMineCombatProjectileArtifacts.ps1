$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$items = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\items.yml')
$java = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()

function Require([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { $errors.Add($message) }
}

foreach ($marker in @(
    'id: combat_crossbow',
    'price_ar: 100',
    'effect: AR_CROSSBOW_TELEPORT',
    'id: cobblestone_trail_bow',
    'price_ar: 64',
    'effect: AR_COBBLESTONE_TRAIL',
    'enchantment: INFINITY',
    'id: explosive_crossbow',
    'price_ar: 300',
    'effect: AR_EXPLOSIVE_CROSSBOW',
    'enchantment: MULTISHOT',
    'id: streamer_stick',
    'source: ADMIN_ONLY',
    'effect: STREAMER_STICK_ARC',
    'custom_model_data: 0'
)) {
    Require $items $marker "Catalog is missing marker: $marker"
}

foreach ($marker in @(
    'ProjectileHitEvent',
    'event.getBow()',
    'keyProjectileAbility',
    'keyProjectileOwner',
    'TNTPrimed',
    'CombatArtifactMath.interpolate',
    'BlockPlaceEvent',
    'STREAMER_STICK_ARC',
    'setVelocity'
)) {
    Require $java $marker "Implementation is missing marker: $marker"
}

if ($errors.Count -gt 0) {
    throw ("Combat projectile artifact validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Combat projectile artifact validation passed.'
