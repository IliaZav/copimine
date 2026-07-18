$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$items = Get-Content -LiteralPath (Join-Path $root 'copimine-artifacts\items.yml') -Raw -Encoding UTF8
$source = Get-Content -LiteralPath (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java') -Raw -Encoding UTF8

$item = [regex]::Match($items, '(?ms)^  - id: craftsman_hammer\r?\n.*?(?=^  - id:|\z)')
if (-not $item.Success) {
    throw 'Craftsman hammer catalog item is missing.'
}

foreach ($expected in @(
    'material: MACE',
    'custom_model_data: 10012',
    'cooldown_seconds: 60',
    'effect: WIND_HAMMER'
)) {
    if ($item.Value -notlike "*$expected*") {
        throw "Wind hammer catalog contract is missing: $expected"
    }
}

if ($item.Value -notmatch 'name:\s*"&6[^\"]+"' -or $item.Value -match 'HASTE_BURST_LONG') {
    throw 'Wind hammer must replace the old haste item definition.'
}

$interact = [regex]::Match($source, '(?s)public void onArtifactInteract\(PlayerInteractEvent var1\) \{.*?(?=\r?\n   @EventHandler)')
if (-not $interact.Success -or
    $interact.Value -notmatch '"WIND_HAMMER"\.equals\(var4\)' -or
    $interact.Value -notmatch 'var5 != Action\.RIGHT_CLICK_BLOCK' -or
    $interact.Value -notmatch 'this\.triggerWindHammer\(var2, var1\.getClickedBlock\(\)\)') {
    throw 'Wind hammer must be triggered only by right-clicking a block.'
}

$registry = [regex]::Match($source, '(?s)private Set<String> artifactInteractEffects\(\) \{.*?(?=\r?\n\s*private )')
if (-not $registry.Success -or $registry.Value -notmatch 'WIND_HAMMER') {
    throw 'Wind hammer must be registered as an interactive artifact effect.'
}

$ability = [regex]::Match($source, '(?s)private boolean triggerWindHammer\(Player player, Block ground\) \{.*?(?=\r?\n\s*private )')
if (-not $ability.Success) {
    throw 'Wind hammer ability implementation is missing.'
}

foreach ($expected in @(
    'getNearbyEntities(center, 10.0D, 10.0D, 10.0D)',
    'distanceSquared(center) > 100.0D',
    'instanceof LivingEntity',
    'setY(Math.max(living.getVelocity().getY(), 0.9D))',
    'PotionEffectType.LEVITATION, 80, 0'
)) {
    if ($ability.Value -notlike "*$expected*") {
        throw "Wind hammer ability contract is missing: $expected"
    }
}

Write-Host 'Wind hammer contract OK'
