$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
$caps = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientCapabilityService.java')
foreach ($needle in @('HEARTBEAT_INTERVAL_MS','sendHeartbeat','TYPE_HEARTBEAT')) {
  if ($client -notmatch [regex]::Escape($needle)) { throw "Client heartbeat marker missing: $needle" }
}
if ($bridge -notmatch 'case ClientBridgePayloads\.HEARTBEAT -> capabilities\.touch') {
  throw 'Server bridge must touch capability state on heartbeat.'
}
if ($caps -notmatch 'heartbeat-timeout') {
  throw 'Capability service must expose heartbeat timeout state.'
}
Write-Host 'CopiMineClient heartbeat validation passed.'
