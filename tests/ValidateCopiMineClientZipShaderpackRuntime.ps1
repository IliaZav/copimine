$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$clientRootCandidates = @(
  (Join-Path $root 'CopiMineClient'),
  (Join-Path $root '..\..\CopiMineClient')
)
$clientRoot = $clientRootCandidates |
  Where-Object { Test-Path -LiteralPath $_ } |
  Select-Object -First 1
if (-not $clientRoot) {
  throw 'CopiMineClient root not found for ZIP shaderpack runtime validation.'
}
$clientRoot = Resolve-Path $clientRoot
$manager = Get-Content -Raw -Encoding UTF8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ShaderRuntimeManager.java')
$iris = Get-Content -Raw -Encoding UTF8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\IrisShaderpackRuntime.java')
$registry = Get-Content -Raw -Encoding UTF8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ShaderpackRegistry.java')
$protocol = Get-Content -Raw -Encoding UTF8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ClientBridgeProtocol.java')

foreach ($needle in @('Route.IRIS_SHADERPACK','shaderpack-first','fallback-post-process')) {
  if ($manager -notmatch [regex]::Escape($needle)) { throw "Shader runtime manager marker missing: $needle" }
}
foreach ($needle in @('switchToShaderpack','restoreState','copimineclient-shader-runtime.properties')) {
  if ($iris -notmatch [regex]::Escape($needle)) { throw "Iris shader runtime marker missing: $needle" }
}
foreach ($needle in @('acid_shaders.zip','crucify.zip','ctr_vcr.zip','cursed_metamorphopsia.zip','lsd_shader.zip','nms_1_6.zip','trippy_shaderpack.zip','white_sharp_1_2.zip')) {
  if ($registry -notmatch [regex]::Escape($needle)) { throw "Shaderpack registry is missing bundled ZIP: $needle" }
}
if ($registry -match [regex]::Escape('jelly_world.zip')) {
  throw 'Shaderpack registry must not include removed jelly_world.zip.'
}
foreach ($needle in @('shaderpackRuntimeAvailable','renderer=shaderpack-first+post-process-fallback','payload.shaderpack()')) {
  if ($protocol -notmatch [regex]::Escape($needle)) { throw "Client bridge protocol marker missing: $needle" }
}
Write-Host 'CopiMineClient ZIP shaderpack runtime validation passed.'
