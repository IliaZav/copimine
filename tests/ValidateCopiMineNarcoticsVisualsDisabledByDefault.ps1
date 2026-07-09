$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('enabled: true','mode: AUTO','allow_client_mod_visuals: true','allow_server_resource_pack_overlay: false','allow_server_particle_fallback: true','!configService.visualsEnabled()','case SERVER_FALLBACK ->')) {
  if (($config + $runtime) -notmatch [regex]::Escape($marker)) { throw "Visual default-safety marker missing: $marker" }
}
Write-Host 'Visual runtime default validation passed.'
