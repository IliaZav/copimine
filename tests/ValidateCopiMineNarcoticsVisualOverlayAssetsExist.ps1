$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$overlayBase = Join-Path $root 'resourcepacks\src\assets\copimine\textures\gui\narcotics'
$shaderBase = Join-Path $root 'resourcepacks\src\assets\copimine\shaders\narcotics'
$overlays = @('desaturate_overlay.png','color_convolve_overlay.png','scan_pincushion_overlay.png','green_noise_overlay.png','invert_overlay.png','wobble_overlay.png','blobs_overlay.png','pencil_overlay.png','chaos_overlay.png')
$shaders = @('desaturate.json','color_convolve.json','scan_pincushion.json','green_noise.json','invert.json','wobble.json','blobs.json','pencil.json','chaos.json')
$missing = @()
$missing += $overlays | Where-Object { -not (Test-Path -LiteralPath (Join-Path $overlayBase $_)) }
$missing += $shaders | Where-Object { -not (Test-Path -LiteralPath (Join-Path $shaderBase $_)) }
if ($missing.Count -gt 0) { throw ("Missing narcotics overlay/shader assets: " + ($missing -join ', ')) }
Write-Host 'Narcotics overlay/shader asset source validation passed.'
