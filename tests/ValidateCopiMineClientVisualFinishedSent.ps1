$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\CopiMineClient.java')
$manager = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java')
if ($client -notmatch 'visualManager\.tick\(ClientBridgeProtocol::sendVisualFinished\)') {
  throw 'Client must forward finished visuals through ClientBridgeProtocol::sendVisualFinished.'
}
if ($manager -notmatch 'finishedHandler\.onFinished\(visual\.seq\(\), visual\.effectId\(\), "duration_elapsed"\)') {
  throw 'ClientVisualManager must emit duration_elapsed finished events.'
}
if ($client -notmatch 'visualManager\.clearAll\(ClientBridgeProtocol::sendVisualFinished,\s*"death"\)') {
  throw 'Client must notify the server when visuals are cleared because of death.'
}
if ($client -notmatch 'visualManager\.clearAll\(ClientBridgeProtocol::sendVisualFinished,\s*"world_change"\)') {
  throw 'Client must notify the server when visuals are cleared because of a world change.'
}
if ($client -notmatch 'visualManager\.clearAll\(ClientBridgeProtocol::sendVisualFinished,\s*"manual"\)') {
  throw 'Client manual clear must notify the server about finished visuals.'
}
Write-Host 'CopiMineClient visual-finished validation passed.'
