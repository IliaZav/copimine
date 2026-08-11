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
    'cooldown_seconds: 15',
    'effect: AR_CROSSBOW_TELEPORT',
    'id: cobblestone_trail_bow',
    'material: BOW',
    'price_ar: 64',
    'cooldown_seconds: 15',
    'effect: AR_COBBLESTONE_TRAIL',
    'id: explosive_crossbow',
    'price_ar: 300',
    'cooldown_seconds: 30',
    'effect: AR_EXPLOSIVE_CROSSBOW',
    'enchantment: MULTISHOT',
    'id: streamer_stick',
    'source: ADMIN_ONLY',
    'effect: STREAMER_STICK_ARC',
    'cooldown_seconds: 15',
    'custom_model_data: 0'
)) {
    Require $items $marker "Catalog is missing marker: $marker"
}

if ($items.Contains('enchantment: INFINITY')) {
    $errors.Add('The cobblestone trail bow must not carry Infinity.')
}

foreach ($marker in @(
    'ProjectileHitEvent',
    'var1.getBow()',
    'keyProjectileAbility',
    'keyProjectileOwner',
    'TNTPrimed',
    'CombatArtifactMath.interpolate',
    'CombatArtifactShotPolicy.decide',
    'BlockPlaceEvent',
    'STREAMER_STICK_ARC',
    'setVelocity',
    'Bukkit.getCurrentTick()'
)) {
    Require $java $marker "Implementation is missing marker: $marker"
}

if ($java.Contains('player.damage(6.0D)')) {
    $errors.Add('Explosive crossbow must not damage its owner.')
}

if ($errors.Count -gt 0) {
    throw ("Combat projectile artifact validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Combat projectile artifact validation passed.'
