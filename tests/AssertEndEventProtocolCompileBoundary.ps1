param(
  [switch]$BuildPlugin,
  [switch]$RequireCompileOnlyOutput
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pluginDir = Join-Path $root 'copimine-end-event'
$pluginJar = Join-Path $pluginDir 'CopiMineEndEvent.jar'

if ($BuildPlugin) {
  & (Join-Path $pluginDir 'build-plugin.ps1')
  if ($LASTEXITCODE -ne 0) {
    throw "Standalone End Event plugin build failed with exit code $LASTEXITCODE"
  }
}

if (-not (Test-Path -LiteralPath $pluginJar -PathType Leaf)) {
  throw "Missing End Event plugin jar: $pluginJar"
}

if ($RequireCompileOnlyOutput) {
  $protocolClass = Join-Path $pluginDir 'build\protocol-classes\me\copimine\protocol\BridgeEnvelopeCodec.class'
  if (-not (Test-Path -LiteralPath $protocolClass -PathType Leaf)) {
    throw "Shared protocol sources were not compiled to the separate compile-only output: $protocolClass"
  }
}

$jarEntries = @(& jar --list --file $pluginJar)
if ($LASTEXITCODE -ne 0) {
  throw "Could not list End Event plugin jar: $pluginJar"
}

$senderClass = 'me/copimine/endevent/bridge/EndEventBridgeV4Sender.class'
if ($jarEntries -notcontains $senderClass) {
  throw "End Event plugin jar is missing its v4 sender: $senderClass"
}

$bundledProtocolClasses = @($jarEntries | Where-Object { $_ -match '^me/copimine/protocol(?:/|$)' })
if ($bundledProtocolClasses.Count -gt 0) {
  $examples = ($bundledProtocolClasses | Select-Object -First 3) -join ', '
  throw "End Event plugin jar must not bundle shared protocol classes: $examples"
}

Write-Host 'END_EVENT_PROTOCOL_COMPILE_BOUNDARY_PASS'
