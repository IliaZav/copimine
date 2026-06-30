. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$runtime = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$clientRoot = Resolve-Path (Join-Path $root '..\..\CopiMineClient')
$client = Read-Utf8 (Join-Path $clientRoot 'src\main\java\me\copimine\client\ClientVisualManager.java')

Require-Contains $runtime 'public boolean supportsOverlayRuntime()' 'VisualRuntimeService must expose overlay capability checks.'
Require-Contains $runtime 'public boolean supportsShaderRuntime()' 'VisualRuntimeService must expose shader capability checks.'
Require-Contains $runtime 'supported via title glyph fallback; may temporarily override other titles' 'Server overlay path must describe its limitation honestly.'
Require-Contains $runtime 'client visuals disabled in config' 'VisualRuntimeService must explain fallback reasons.'
foreach ($marker in @('drawNoiseGrid','drawColorShiftPass','drawScanPulse','drawVignette','drawHatch','drawSegmentedTexture')) {
  Require-Contains $client $marker "ClientVisualManager must contain upgraded renderer helper $marker."
}

Throw-IfErrors 'ValidateCopiMineClientVisualRuntimeHonestAndUpgraded'
