$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
foreach ($rel in @('copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java','copimine-narcotics\src\me\copimine\clientbridge\ClientCapabilityService.java','copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java','copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')) {
  if (-not (Test-Path (Join-Path $root $rel))) { throw "Missing client bridge file: $rel" }
}
Write-Host 'Client bridge file validation passed.'