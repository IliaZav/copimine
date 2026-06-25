$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('CLIENT_MOD_VISUAL','SERVER_RESOURCE_PACK_OVERLAY','SERVER_PARTICLE_FALLBACK','clientRouteAvailable','overlayRouteAvailable','applyServerFallback')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { throw "Fallback routing marker missing: $marker" }
}
Write-Host 'Client bridge fallback validation passed.'