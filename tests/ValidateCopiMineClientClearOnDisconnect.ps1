$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\CopiMineClient.java')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
foreach ($needle in @('ClientPlayConnectionEvents.DISCONNECT','visualManager.clearAll("disconnect")','ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE')) {
  if ($client -notmatch [regex]::Escape($needle)) { throw "Client cleanup marker missing: $needle" }
}
foreach ($needle in @('public void onQuit(PlayerQuitEvent event)','public void onDeath(PlayerDeathEvent event)','public void onWorldChange(PlayerChangedWorldEvent event)')) {
  if ($bridge -notmatch [regex]::Escape($needle)) { throw "Server cleanup marker missing: $needle" }
}
Write-Host 'CopiMineClient clear-on-disconnect validation passed.'
