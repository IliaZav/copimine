$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('visuals status','supportsOverlayRuntime()','supportsShaderRuntime()','overlaySupportReason()','shaderSupportReason()','resolvedModeFor(sampleEffect)')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Visual capability status marker missing: $marker" }
}
Write-Host 'Narcotics visual status capability validation passed.'
