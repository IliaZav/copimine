$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$explosion = [regex]::Match($source, '(?s)private void simulateWrongMixExplosion\(Block block\) \{.*?(?=\r?\n\s*private boolean queueIngredients)')

if (-not $explosion.Success -or $explosion.Value -match '\.damage\(') {
    throw 'Wrong-mix effects must not bypass PvP and region-protection handlers with direct damage.'
}

Write-Host 'Narcotics wrong-mix damage attribution contract OK'
