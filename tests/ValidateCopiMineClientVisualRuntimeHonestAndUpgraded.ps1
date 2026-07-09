. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$runtime = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$clientRootCandidates = @(
  (Join-Path $root 'CopiMineClient'),
  (Join-Path $root '..\..\CopiMineClient')
)
$clientRoot = $clientRootCandidates |
  Where-Object { Test-Path -LiteralPath $_ } |
  Select-Object -First 1
if (-not $clientRoot) {
  throw 'CopiMineClient root not found for visual runtime validation.'
}
$clientRoot = Resolve-Path $clientRoot
$client = Read-Utf8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ClientVisualManager.java')
$shaderManager = Read-Utf8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ShaderRuntimeManager.java')
$irisRuntime = Read-Utf8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\IrisShaderpackRuntime.java')
$registry = Read-Utf8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ShaderpackRegistry.java')

Require-Contains $runtime 'public boolean supportsOverlayRuntime()' 'VisualRuntimeService must expose overlay capability checks.'
Require-Contains $runtime 'public boolean supportsShaderRuntime()' 'VisualRuntimeService must expose shader capability checks.'
Require-Contains $runtime 'supportsClientZipShaderpackRuntime()' 'VisualRuntimeService must expose ZIP shaderpack capability checks.'
Require-Contains $runtime 'server falls back to light particles only' 'Server fallback path must describe its limitation honestly.'
Require-Contains $runtime 'client visuals disabled in config' 'VisualRuntimeService must explain fallback reasons.'
Require-Contains $runtime 'client_zip_shaderpack_runtime_supported' 'VisualRuntimeService must use the ZIP shaderpack manifest flag.'
foreach ($marker in @('drawEffect','drawNoiseGrid','drawColorShiftPass','drawScanPulse','drawVignette','drawHatch')) {
  Require-Contains $client $marker "ClientVisualManager must contain upgraded renderer helper $marker."
}
foreach ($marker in @('Route.IRIS_SHADERPACK','Route.FALLBACK_POST_PROCESS','fallback-post-process')) {
  Require-Contains $shaderManager $marker "ShaderRuntimeManager must describe shaderpack-first runtime marker $marker."
}
foreach ($marker in @('switchToShaderpack','restoreState','copimineclient-shader-runtime.properties')) {
  Require-Contains $irisRuntime $marker "IrisShaderpackRuntime must persist and restore shaderpack state via $marker."
}
Require-Contains $registry 'jelly_world.zip' 'ShaderpackRegistry must track jelly_world.zip explicitly.'
Require-Contains $registry 'FALLBACK_ONLY' 'ShaderpackRegistry must mark non-Iris packs honestly.'

Throw-IfErrors 'ValidateCopiMineClientVisualRuntimeHonestAndUpgraded'
