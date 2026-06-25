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
Write-Host 'CopiMineClient visual-finished validation passed.'
