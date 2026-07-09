$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
if ($manifest -notmatch '"server_overlay_supported": false' -or $manifest -notmatch 'SERVER_PARTICLE_FALLBACK') { throw 'Server overlay must stay disabled and particle fallback documented.' }
Write-Host 'Narcotics server-overlay-disabled manifest validation passed.'
