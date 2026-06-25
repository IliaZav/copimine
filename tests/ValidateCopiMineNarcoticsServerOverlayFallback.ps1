$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
if ($manifest -notmatch 'server_overlay_supported' -or $manifest -notmatch 'font_title_glyph') { throw 'Server overlay manifest markers missing.' }
Write-Host 'Narcotics server overlay manifest validation passed.'