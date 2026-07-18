$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$explosion = [regex]::Match($source, '(?s)private void simulateWrongMixExplosion\(Block block, org\.bukkit\.entity\.Player initiator\) \{.*?(?=\r?\n\s*private boolean queueIngredients)')

if (-not $explosion.Success -or $explosion.Value -notmatch 'nearby\.damage\([^;]*initiator\)') {
    throw 'Wrong-mix damage must retain the initiating player so normal PvP and region-protection handlers can evaluate it.'
}

Write-Host 'Narcotics wrong-mix damage attribution contract OK'
