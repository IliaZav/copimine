$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('configService.visualsForceDisabled()','return VisualRoute.CLIENT_MOD_VISUAL','CLIENT_MOD_VISUAL','SERVER_RESOURCE_PACK_OVERLAY','SERVER_PARTICLE_FALLBACK')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { throw "Visual route priority marker missing: $marker" }
}
# The shader route is chosen by consumption, not by the visuals settings.
foreach ($forbidden in @('case AUTO ->','configService.visualMode()','configService.visualsEnabled()')) {
  if ($runtime -match [regex]::Escape($forbidden)) { throw "Visual route must not follow settings: $forbidden" }
}
Write-Host 'Narcotics visual route priority validation passed.'