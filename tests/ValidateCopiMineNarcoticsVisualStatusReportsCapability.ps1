$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

$required = @(
  'visuals status',
  'supportsServerParticleFallback()',
  'resolvedModeFor(sampleEffect)',
  'overlayTextures().size()'
)
foreach ($marker in $required) {
  if ($main -notmatch [regex]::Escape($marker)) {
    throw "Visual capability status marker missing: $marker"
  }
}

# Screen shaders were retired: the status output must not advertise them as available.
$forbidden = @('supportsShaderRuntime()', 'shaderSupportReason()', 'supportsClientZipShaderpackRuntime()')
foreach ($marker in $forbidden) {
  if ($main -match [regex]::Escape($marker)) {
    throw "Visual status must not advertise retired shader capability: $marker"
  }
}

Write-Host 'Narcotics visual status capability validation passed.'
