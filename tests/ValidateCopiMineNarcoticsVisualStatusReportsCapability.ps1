$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

$required = @(
  'visuals status',
  'supportsShaderRuntime()',
  'shaderSupportReason()',
  'resolvedModeFor(sampleEffect)'
)
foreach ($marker in $required) {
  if ($main -notmatch [regex]::Escape($marker)) {
    throw "Visual capability status marker missing: $marker"
  }
}

$overlayMarkers = @('supportsServerOverlayRuntime()', 'serverOverlaySupportReason()', 'supportsOverlayRuntime()', 'overlaySupportReason()')
if (-not (($overlayMarkers | Where-Object { $main -match [regex]::Escape($_) }).Count -ge 2)) {
  throw 'Visual capability status markers for overlay support/reason are missing.'
}

Write-Host 'Narcotics visual status capability validation passed.'
