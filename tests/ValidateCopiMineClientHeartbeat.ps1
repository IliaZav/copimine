$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tools\minecraft-26.3\client\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
$caps = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientCapabilityService.java')
foreach ($needle in @('HEARTBEAT_INTERVAL_MS','sendHeartbeat','TYPE_HEARTBEAT')) {
  if ($client -notmatch [regex]::Escape($needle)) { throw "Client heartbeat marker missing: $needle" }
}
if ($bridge -notmatch '(?s)case ClientBridgePayloads\.HEARTBEAT -> \{\s*if \(isVerifiedClientMessage\(player, payload\)\) \{\s*capabilities\.touch\(player, payload\.sessionId\(\)\);') {
  throw 'Server bridge must update capability state only for a verified client heartbeat.'
}
if ($caps -notmatch 'heartbeat-timeout') {
  throw 'Capability service must expose heartbeat timeout state.'
}
Write-Host 'CopiMineClient heartbeat validation passed.'
