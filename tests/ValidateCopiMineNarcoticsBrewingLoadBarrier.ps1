$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$preload = [regex]::Match($source, '(?s)public void preloadCacheIfEnabled\(\) \{.*?(?=\r?\n\s*public void runIntegritySweep)')
$add = [regex]::Match($source, '(?s)public boolean tryAddIngredient\(org\.bukkit\.entity\.Player player, Block block, ItemStack stack\) \{.*?(?=\r?\n\s*public void handleCauldronBroken)')

if ($source -notmatch 'private volatile boolean cacheReady = false;' -or -not $preload.Success -or $preload.Value -notmatch 'cache\.merge\(' -or $preload.Value -notmatch 'cacheReady = true;' -or $preload.Value -notmatch 'runTaskLater') {
    throw 'Brewing-state preload must have a retrying readiness barrier and never overwrite a newer in-memory state.'
}

if (-not $add.Success -or $add.Value -notmatch 'if \(!cacheReady\)' -or $add.Value -notmatch 'return false;') {
    throw 'Cauldrons must refuse ingredients while persisted brewing state is still loading.'
}

Write-Host 'Narcotics brewing load barrier contract OK'
