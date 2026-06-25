$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('applyServerOverlay','applyServerFallback','SERVER_RESOURCE_PACK_OVERLAY','SERVER_PARTICLE_FALLBACK')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { throw "Server fallback marker missing: $marker" }
}
Write-Host 'Narcotics server fallback validation passed.'