$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$zipPath = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
if (-not (Test-Path $zipPath)) { throw "Missing resource pack zip: $zipPath" }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
  foreach ($entry in @(
    'assets/copimine/manifests/narcotics_items_manifest.json',
    'assets/copimine/manifests/narcotics_visuals_manifest.json',
    'assets/copimine/font/narcotics_overlay.json',
    'assets/copimine/models/item/feta.json',
    'assets/copimine/models/item/kola.json',
    'assets/copimine/models/item/girion.json',
    'assets/copimine/models/item/sbp.json',
    'assets/copimine/models/item/sos.json',
    'assets/copimine/models/item/drun.json',
    'assets/copimine/models/item/chups.json',
    'assets/copimine/models/item/borshevik.json',
    'assets/copimine/models/item/zhuzevo.json',
    'assets/copimine/textures/item/narcotics/feta.png',
    'assets/copimine/textures/item/narcotics/kola.png',
    'assets/copimine/textures/item/narcotics/girion.png',
    'assets/copimine/textures/item/narcotics/sbp.png',
    'assets/copimine/textures/item/narcotics/sos.png',
    'assets/copimine/textures/item/narcotics/drun.png',
    'assets/copimine/textures/item/narcotics/chups.png',
    'assets/copimine/textures/item/narcotics/borshevik.png',
    'assets/copimine/textures/item/narcotics/zhuzevo.png',
    'assets/copimine/textures/gui/narcotics/desaturate_overlay.png',
    'assets/copimine/textures/gui/narcotics/color_convolve_overlay.png',
    'assets/copimine/textures/gui/narcotics/scan_pincushion_overlay.png',
    'assets/copimine/textures/gui/narcotics/green_noise_overlay.png',
    'assets/copimine/textures/gui/narcotics/invert_overlay.png',
    'assets/copimine/textures/gui/narcotics/wobble_overlay.png',
    'assets/copimine/textures/gui/narcotics/blobs_overlay.png',
    'assets/copimine/textures/gui/narcotics/pencil_overlay.png',
    'assets/copimine/textures/gui/narcotics/chaos_overlay.png'
  )) {
    if (-not ($zip.Entries | Where-Object { $_.FullName -eq $entry })) {
      throw "Missing narcotics resource pack zip entry: $entry"
    }
  }
} finally {
  $zip.Dispose()
}
Write-Host 'Narcotics resource pack zip contains required assets.'
