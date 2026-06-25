$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\ValidateCopiMineNarcoticsVisualRoutePriority.ps1"
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
foreach ($needle in @('CLIENT_MOD_VISUAL','SERVER_RESOURCE_PACK_OVERLAY','SERVER_PARTICLE_FALLBACK')) {
  if ($manifest -notmatch [regex]::Escape($needle)) { throw "Visual route priority manifest marker missing: $needle" }
}
Write-Host 'Visual runtime route-priority validation passed.'
