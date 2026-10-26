$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('SERVER_PARTICLE_FALLBACK','applyServerFallback','allowServerParticleFallback()')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { throw "Server fallback marker missing: $marker" }
}
# Screen shaders are retired, so nothing may route visuals to the client mod any more.
foreach ($marker in @('sendVisualStart','sendVisualRefresh')) {
  if ($runtime -match [regex]::Escape($marker)) { throw "Retired narcotics shader route must not be started: $marker" }
}
Write-Host 'Client bridge fallback validation passed.'