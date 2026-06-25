$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$visual = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('detectOverlaySupport()', 'detectShaderSupport()', 'hasAllOverlayAssets()', 'hasAllShaderProfiles()', 'manifestFlag("overlay_supported")', 'manifestFlag("shader_supported")')) {
  if ($visual -notmatch [regex]::Escape($marker)) { throw "Capability detection marker missing: $marker" }
}
Write-Host 'Visual runtime capability detection validation passed.'
