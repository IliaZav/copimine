$ErrorActionPreference = 'Stop'

$servicePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java'
$dbPath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java'
$pluginPath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$service = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8
$database = Get-Content -LiteralPath $dbPath -Raw -Encoding UTF8
$plugin = Get-Content -LiteralPath $pluginPath -Raw -Encoding UTF8

$preload = [regex]::Match($service, '(?s)public void preloadCacheIfEnabled\(\) \{.*?(?=\r?\n\s*public void runIntegritySweep)')
if (-not $preload.Success -or
    $preload.Value -match 'clearBrewingStates\(' -or
    $preload.Value -notmatch 'database\.loadBrewingStates\(\)' -or
    $preload.Value -notmatch 'cache\.put\(' -or
    $preload.Value -notmatch 'Bukkit\.getScheduler\(\)\.runTask\(plugin') {
    throw 'Brewing states must be restored into the main-thread cache rather than cleared at startup.'
}

if ($database -notmatch 'Instant\.now\(\)\.toEpochMilli\(\)') {
    throw 'Brewing state timestamps must be persisted in milliseconds to match stale-state checks.'
}

if ($service -notmatch 'public void reconcileLoadedChunk\(' -or
    $plugin -notmatch 'cauldronService\.reconcileLoadedChunk\(event\.getWorld\(\)\.getName\(\), event\.getChunk\(\)\.getX\(\), event\.getChunk\(\)\.getZ\(\)\)') {
    throw 'A loaded chunk must reconcile only its own restored cauldrons instead of starting a global sweep.'
}

Write-Host 'Narcotics brewing restore contract OK'
