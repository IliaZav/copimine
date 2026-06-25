$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$dir = Join-Path $root 'CopiMineClient\src\main\resources\assets\copimineclient\textures\visuals'
foreach ($name in @('desaturate_overlay.png','color_convolve_overlay.png','scan_pincushion_overlay.png','green_noise_overlay.png','invert_overlay.png','wobble_overlay.png','blobs_overlay.png','pencil_overlay.png','chaos_overlay.png','noise.png','vignette.png','scanlines.png')) {
  $file = Join-Path $dir $name
  if (-not (Test-Path $file)) { throw "Missing client visual asset: $name" }
  if ((Get-Item $file).Length -lt 512) { throw "Client visual asset looks blank or too small: $name" }
}
Write-Host 'CopiMineClient asset validation passed.'
