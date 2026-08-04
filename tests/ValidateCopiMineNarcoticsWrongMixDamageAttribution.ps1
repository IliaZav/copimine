$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$explosion = [regex]::Match($source, '(?s)private void simulateWrongMixExplosion\(Block block\) \{.*?(?=\r?\n\s*private boolean queueIngredients)')

$validDamageContract = $explosion.Success `
    -and $explosion.Value -match 'getNearbyEntities' `
    -and $explosion.Value -match 'distanceSquared' `
    -and $explosion.Value -match '\.damage\(' `
    -and $source -match 'WRONG_MIX_MIN_DAMAGE = 14\.0D' `
    -and $source -match 'WRONG_MIX_MAX_DAMAGE = 20\.0D' `
    -and $source -match 'WRONG_MIX_DAMAGE_RADIUS = 6\.0D'
if (-not $validDamageContract) {
    throw 'Wrong-mix effects must damage nearby players for 7-10 hearts only within six blocks.'
}

Write-Host 'Narcotics wrong-mix radius and damage contract OK'
