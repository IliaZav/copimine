$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tools\minecraft-26.3\client\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$payload = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tools\minecraft-26.3\client\src\main\java\me\copimine\client\BridgePayload.java')
$server = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')
$policy = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tools\minecraft-26.3\protocol\src\main\java\me\copimine\protocol\ClientModHandshakePolicy.java')
foreach ($needle in @('PROTOCOL_VERSION = ClientModHandshakePolicy.PROTOCOL_VERSION','TYPE_VISUAL_ACK','TYPE_VISUAL_FINISHED','sendVisualAck','sendVisualFinished','lastAckSeq')) {
  if ($client -notmatch [regex]::Escape($needle)) { throw "Client protocol v3 marker missing: $needle" }
}
foreach ($needle in @('long seq','visualAck','visualFinished','writeLong(Math.max(0L, seq))','String shaderpack','int fadeInMillis','int fadeOutMillis')) {
  if ($payload -notmatch [regex]::Escape($needle)) { throw "BridgePayload seq/ack marker missing: $needle" }
}
foreach ($needle in @('public static final int PROTOCOL_VERSION = ClientModHandshakePolicy.PROTOCOL_VERSION','VISUAL_ACK','VISUAL_FINISHED','encodeVisualStart(long seq','String shaderpack','fadeInMillis','fadeOutMillis')) {
  if ($server -notmatch [regex]::Escape($needle)) { throw "Server protocol v3 marker missing: $needle" }
}
if ($policy -notmatch 'PROTOCOL_VERSION = 3') { throw 'Migration handshake policy must define the protocol v3 compatibility boundary.' }
Write-Host 'CopiMineClient protocol-v3 seq/ack validation passed.'
