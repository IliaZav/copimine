$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
$visual = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$font = Join-Path $root 'resourcepacks\src\assets\copimine\font\narcotics_overlay.json'
if ($manifest -match '"overlay_supported"\s*:\s*true') {
  if (-not (Test-Path $font)) { throw 'Overlay is marked supported but font manifest is missing.' }
  foreach ($marker in @('applyOverlay(', 'copimine:narcotics_overlay', 'player.showTitle(title)')) {
    if ($visual -notmatch [regex]::Escape($marker)) { throw "Overlay runtime marker missing: $marker" }
  }
} else {
  if ($manifest -notmatch 'overlay_runtime') { throw 'Overlay unsupported but not documented in manifest.' }
}
Write-Host 'Overlay runtime support/documentation validation passed.'
