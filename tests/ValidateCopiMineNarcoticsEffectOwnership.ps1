$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)private void clearTransientEffects\(Player player, boolean clearVisuals\) \{.*?(?=\r?\n\s*private )')

if (-not $method.Success) {
    throw 'Could not locate narcotics transient-effect cleanup.'
}

if ($method.Value -match 'player\.removePotionEffect\(PotionEffectType\.(DARKNESS|NAUSEA)\)') {
    throw 'Narcotics cleanup must only remove effects it recorded as its own.'
}

if ($method.Value -notmatch 'trackedEffects\.remove\(player\.getUniqueId\(\)\)') {
    throw 'Narcotics cleanup must use the tracked effect ownership list.'
}

Write-Host 'Narcotics effect ownership contract OK'
