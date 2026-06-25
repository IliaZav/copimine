$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$packRoot = Join-Path $root 'resourcepacks'
$zipPath = Join-Path $packRoot 'build\CopiMineResourcePack.zip'
$shaPath = Join-Path $packRoot 'build\CopiMineResourcePack.sha1'
$serverPropsPath = Join-Path $root 'minecraft\server\server.properties'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in @($zipPath,$shaPath,$serverPropsPath)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing resource pack build artifact: $path") }
}

if (Test-Path $zipPath) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
  try {
    foreach ($entry in @(
      'pack.mcmeta',
      'pack.png',
      'assets/copimine/models/item/zmei_gorynych.json',
      'assets/copimine/models/item/feta.json',
      'assets/copimine/models/item/kola.json',
      'assets/copimine/models/item/girion.json',
      'assets/copimine/models/item/sbp.json',
      'assets/copimine/models/item/sos.json',
      'assets/copimine/models/item/drun.json',
      'assets/copimine/models/item/chups.json',
      'assets/copimine/models/item/borshevik.json',
      'assets/copimine/models/item/zhuzevo.json',
      'assets/copimine/manifests/narcotics_items_manifest.json',
      'assets/copimine/manifests/narcotics_visuals_manifest.json',
      'assets/copimine/font/narcotics_overlay.json',
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
      'assets/copimine/textures/gui/narcotics/chaos_overlay.png',
      'assets/copimine/shaders/narcotics/desaturate.json',
      'assets/copimine/shaders/narcotics/color_convolve.json',
      'assets/copimine/shaders/narcotics/scan_pincushion.json',
      'assets/copimine/shaders/narcotics/green_noise.json',
      'assets/copimine/shaders/narcotics/invert.json',
      'assets/copimine/shaders/narcotics/wobble.json',
      'assets/copimine/shaders/narcotics/blobs.json',
      'assets/copimine/shaders/narcotics/pencil.json',
      'assets/copimine/shaders/narcotics/chaos.json',
      'assets/minecraft/models/item/netherite_sword.json',
      'assets/minecraft/models/item/white_dye.json',
      'assets/minecraft/models/item/sugar.json',
      'assets/minecraft/models/item/slime_ball.json',
      'assets/minecraft/models/item/gold_nugget.json',
      'assets/minecraft/models/item/bone_meal.json',
      'assets/minecraft/models/item/paper.json',
      'assets/minecraft/models/item/blue_stained_glass_pane.json',
      'assets/minecraft/models/item/kelp.json',
      'assets/minecraft/models/item/brown_dye.json'
    )) {
      if (-not ($zip.Entries | Where-Object { $_.FullName -eq $entry })) {
        $errors.Add("Resource pack zip missing entry: $entry")
      }
    }
  } finally {
    $zip.Dispose()
  }
}

if ((Test-Path $zipPath) -and (Test-Path $shaPath)) {
  $expectedSha = (Get-Content -Raw -Encoding UTF8 $shaPath).Trim()
  $actualSha = (Get-FileHash -Algorithm SHA1 -LiteralPath $zipPath).Hash.ToLowerInvariant()
  if ($expectedSha -ne $actualSha) { $errors.Add("Resource pack SHA1 file does not match zip. Expected $expectedSha but got $actualSha") }
}

if ((Test-Path $shaPath) -and (Test-Path $serverPropsPath)) {
  $expectedSha = (Get-Content -Raw -Encoding UTF8 $shaPath).Trim()
  $serverProps = Get-Content -Raw -Encoding UTF8 $serverPropsPath
  if ($serverProps -notmatch [regex]::Escape("resource-pack-sha1=$expectedSha")) {
    $errors.Add('server.properties resource-pack-sha1 is not synchronized with the built resource pack.')
  }
}

if ($errors.Count -gt 0) {
  throw ("Resource pack build validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Resource pack build validation passed: Phase 1 narcotics assets, SHA1 file, and server.properties hash are synchronized.'
