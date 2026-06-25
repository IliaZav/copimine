$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$payload = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\BridgePayload.java')
$server = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')
foreach ($needle in @('PROTOCOL_VERSION = 2','TYPE_VISUAL_ACK','TYPE_VISUAL_FINISHED','sendVisualAck','sendVisualFinished','lastAckSeq')) {
  if ($client -notmatch [regex]::Escape($needle)) { throw "Client protocol v2 marker missing: $needle" }
}
foreach ($needle in @('long seq','visualAck','visualFinished','writeLong(Math.max(0L, seq))')) {
  if ($payload -notmatch [regex]::Escape($needle)) { throw "BridgePayload seq/ack marker missing: $needle" }
}
foreach ($needle in @('public static final int PROTOCOL_VERSION = 2','VISUAL_ACK','VISUAL_FINISHED','encodeVisualStart(long seq')) {
  if ($server -notmatch [regex]::Escape($needle)) { throw "Server protocol v2 marker missing: $needle" }
}
Write-Host 'CopiMineClient protocol-v2 seq/ack validation passed.'
