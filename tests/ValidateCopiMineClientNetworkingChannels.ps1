$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$readyClient = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientReadyProtocol.java')
$server = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')
$readyServer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientReadyPayloads.java')
if ($client -notmatch 'copimine:client_bridge' -or $server -notmatch 'copimine:client_bridge') { throw 'Client and server bridge channels must match.' }
if ($readyClient -notmatch 'copimine:client_ready' -or $readyClient -notmatch 'copimine:client_ready_ack') { throw 'Client READY/ACK channels must be registered.' }
if ($readyServer -notmatch 'copimine:client_ready' -or $readyServer -notmatch 'copimine:client_ready_ack') { throw 'Server READY/ACK channels must be registered.' }
foreach ($needle in @('visual_start','visual_stop','visual_clear_all','visual_ack','visual_finished','heartbeat')) {
  if ($client -notmatch [regex]::Escape($needle) -or $server -notmatch [regex]::Escape($needle)) {
    throw "Client/server bridge marker missing: $needle"
  }
}
Write-Host 'CopiMineClient networking channel validation passed.'
