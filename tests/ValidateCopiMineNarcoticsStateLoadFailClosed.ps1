$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$preload = [regex]::Match($source, '(?s)public void preloadState\(UUID playerUuid\) \{.*?(?=\r?\n\s*public boolean isStateReady)')
$zhuzevo = [regex]::Match($source, '(?s)private PlayerState applyZhuzevo\(Player player, NarcoticDefinition definition, PlayerState state, long now\) \{.*?(?=\r?\n\s*private void applyConfiguredEffects)')

if ($source -notmatch 'Set<UUID> readyStates' -or -not $preload.Success -or $preload.Value -notmatch 'readyStates\.add\(playerUuid\)' -or $preload.Value -notmatch 'readyStates\.remove\(playerUuid\)' -or $source -notmatch 'readyStates\.contains\(player\.getUniqueId\(\)\)') {
    throw 'Narcotics consumption must stay blocked until persistent player state has loaded successfully.'
}

if (-not $zhuzevo.Success -or $zhuzevo.Value -notmatch 'state\.overdoseUntil\(\) > now \? state\.lastItemId\(\) : definition\.id\(\)') {
    throw 'Zhuzhevo must not replace the persisted source item of an already active overdose.'
}

Write-Host 'Narcotics state load fail-closed contract OK'
