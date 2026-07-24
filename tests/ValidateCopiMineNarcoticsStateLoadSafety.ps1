$ErrorActionPreference = 'Stop'

$servicePath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java'
$pluginPath = Join-Path $PSScriptRoot '..\copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java'
$service = Get-Content -LiteralPath $servicePath -Raw -Encoding UTF8
$plugin = Get-Content -LiteralPath $pluginPath -Raw -Encoding UTF8

if ($service -notmatch 'Set<UUID> loadingStates' -or
    $service -notmatch 'public boolean isStateReady\(Player player\)' -or
    $service -notmatch 'loadingStates\.add\(playerUuid\)' -or
    $service -notmatch 'loadingStates\.remove\(playerUuid\)') {
    throw 'Overdose state loading must explicitly block consumption until a player state is ready.'
}

$preload = [regex]::Match($service, '(?s)public void preloadState\(UUID playerUuid\) \{.*?(?=\r?\n\s*public void consume)')
if (-not $preload.Success -or
    $preload.Value -notmatch 'Bukkit\.getScheduler\(\)\.runTask\(plugin' -or
    $preload.Value -notmatch 'restoreActiveOverdose\(' -or
    $preload.Value -notmatch 'runTaskLater\(plugin, \(\) -> preloadState\(playerUuid\), delay\)' -or
    $preload.Value -match 'states\.put\(playerUuid, PlayerState\.empty' -or
    $preload.Value -match 'readyStates\.add\(playerUuid\).*MAX_PRELOAD_RETRIES') {
    throw 'Loaded overdose state must restore remaining penalties on the main thread and retry safely after a database failure.'
}

$interact = [regex]::Match($plugin, '(?s)public void onInteract\(PlayerInteractEvent event\) \{.*?(?=\r?\n\s*@EventHandler\s*\r?\n\s*public void onChunkLoad)')
if (-not $interact.Success -or
    $interact.Value -notmatch '!overdoseService\.isStateReady\(player\)' -or
    $interact.Value -notmatch 'event\.setCancelled\(true\)') {
    throw 'Official narcotic use must be cancelled before consuming an item while state is loading.'
}

if ($plugin -notmatch 'public void onRespawn\(PlayerRespawnEvent event\)' -or
    $plugin -notmatch 'overdoseService\.restoreActiveOverdose\(event\.getPlayer\(\)\)') {
    throw 'A saved overdose must be restored after player respawn.'
}

Write-Host 'Narcotics state load safety contract OK'
