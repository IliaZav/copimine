$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('return particleRoute();','SERVER_RESOURCE_PACK_OVERLAY','SERVER_PARTICLE_FALLBACK','private VisualRoute particleRoute()')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { throw "Visual route priority marker missing: $marker" }
}
if ($runtime -match 'sendVisualStart|sendVisualRefresh') { throw 'Retired shader route must not start or refresh a client visual.' }
Write-Host 'Narcotics particle-only route priority validation passed.'