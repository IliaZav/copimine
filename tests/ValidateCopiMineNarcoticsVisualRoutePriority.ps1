$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('case AUTO -> firstAvailable(player, effectId, configService.preferClientVisuals(), configService.fallbackToServerOverlay())','CLIENT_MOD_VISUAL','SERVER_RESOURCE_PACK_OVERLAY','SERVER_PARTICLE_FALLBACK')) {
  if ($runtime -notmatch [regex]::Escape($marker)) { throw "Visual route priority marker missing: $marker" }
}
Write-Host 'Narcotics visual route priority validation passed.'