$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('enabled: true','force_disable: false','allow_server_resource_pack_overlay: false','configService.visualsForceDisabled()','return VisualRoute.CLIENT_MOD_VISUAL','return VisualRoute.SERVER_PARTICLE_FALLBACK')) {
  if (($config + $runtime) -notmatch [regex]::Escape($marker)) { throw "Visual default-safety marker missing: $marker" }
}
# The shader belongs to the consumption event: operator/player settings must not drive it.
foreach ($forbidden in @('configService.visualMode()','configService.visualsEnabled()','configService.allowClientModVisuals()')) {
  if ($runtime -match [regex]::Escape($forbidden)) { throw "Visual route must not follow settings: $forbidden" }
}
Write-Host 'Visual runtime default validation passed.'