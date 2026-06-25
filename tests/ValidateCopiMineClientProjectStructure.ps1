$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$client = Join-Path $root 'CopiMineClient'
foreach ($rel in @('build.gradle','settings.gradle','gradle.properties','src\main\resources\fabric.mod.json','src\main\java\me\copimine\client\CopiMineClient.java','src\main\java\me\copimine\client\ClientBridgeProtocol.java','src\main\java\me\copimine\client\ClientVisualManager.java','README_INSTALL_RU.md','PROTOCOL.md','ASSET_LICENSES.md')) {
  if (-not (Test-Path (Join-Path $client $rel))) { throw "Missing CopiMineClient file: $rel" }
}
Write-Host 'CopiMineClient project structure validation passed.'
