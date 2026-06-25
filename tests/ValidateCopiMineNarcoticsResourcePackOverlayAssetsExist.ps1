$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$dir = Join-Path $root 'resourcepacks\src\assets\copimine\textures\gui\narcotics'
foreach ($name in @('desaturate_overlay.png','color_convolve_overlay.png','scan_pincushion_overlay.png','green_noise_overlay.png','invert_overlay.png','wobble_overlay.png','blobs_overlay.png','pencil_overlay.png','chaos_overlay.png')) {
  if (-not (Test-Path (Join-Path $dir $name))) { throw "Missing overlay asset: $name" }
}
Write-Host 'Resource pack overlay asset validation passed.'