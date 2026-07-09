$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
foreach ($needle in @('"server_overlay_supported": false','"client_mod_visual_supported": true','"client_zip_shaderpack_runtime_supported": true','"true_shader_runtime_supported": false','"server_forceable_shader_supported": false')) {
  if ($manifest -notmatch [regex]::Escape($needle)) { throw "Visual manifest flag missing: $needle" }
}
Write-Host 'Resource pack honest-visual-flags validation passed.'
