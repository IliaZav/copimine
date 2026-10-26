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
Require-Contains $runtime 'supportsServerParticleFallback()' 'VisualRuntimeService must expose the particle fallback capability.'
Require-Contains $runtime 'narcotics screen shaders retired' 'Server fallback path must describe its limitation honestly.'
Require-Contains $runtime 'client visuals disabled in config' 'VisualRuntimeService must explain fallback reasons.'
foreach ($retired in @('sendVisualStart','sendVisualRefresh','client_zip_shaderpack_runtime_supported')) {
  if ($runtime -match [regex]::Escape($retired)) { throw "Retired narcotics shader surface must not be used: $retired" }
}
foreach ($marker in @('drawEffect','drawNoiseGrid','drawColorShiftPass','drawScanPulse','drawVignette','drawHatch')) {
  Require-Contains $client $marker "ClientVisualManager must contain upgraded renderer helper $marker."
}
foreach ($marker in @('Route.IRIS_SHADERPACK','Route.FALLBACK_POST_PROCESS','fallback-post-process')) {
  Require-Contains $shaderManager $marker "ShaderRuntimeManager must describe shaderpack-first runtime marker $marker."
}
foreach ($marker in @('switchToShaderpack','restoreState','copimineclient-shader-runtime.properties')) {
  Require-Contains $irisRuntime $marker "IrisShaderpackRuntime must persist and restore shaderpack state via $marker."
}
Require-Contains $registry 'white_sharp_1_2.zip' 'ShaderpackRegistry must keep white_sharp_1_2.zip as a spare embedded Iris shaderpack.'
if ($registry -match [regex]::Escape('jelly_world.zip')) {
  $errors.Add('ShaderpackRegistry must not embed jelly_world.zip after the Jelly World removal.')
}

Throw-IfErrors 'ValidateCopiMineClientVisualRuntimeHonestAndUpgraded'
