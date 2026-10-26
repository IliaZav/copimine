$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$visual = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('configService.allowServerParticleFallback()', 'configService.fallbackToParticles()', 'configService.visualsEnabled()', 'configService.isVisualEffectEnabled(')) {
  if ($visual -notmatch [regex]::Escape($marker)) { throw "Capability detection marker missing: $marker" }
}
foreach ($method in @('supportsShaderRuntime()', 'supportsOverlayRuntime()')) {
  if ($visual -notmatch ([regex]::Escape($method) + '\s*\{\s*return false;\s*\}')) {
    throw "$method must stay explicitly false for the particle-only design."
  }
}
Write-Host 'Visual runtime capability detection validation passed.'