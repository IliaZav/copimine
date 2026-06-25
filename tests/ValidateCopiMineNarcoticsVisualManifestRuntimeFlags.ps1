$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
foreach ($marker in @('"overlay_supported"', '"shader_supported"', '"runtime_supported"', '"shader_runtime_supported"', '"placeholder_only"', '"source"', '"license"')) {
  if ($manifest -notmatch [regex]::Escape($marker)) { throw "Visual manifest flag missing: $marker" }
}
Write-Host 'Narcotics visuals manifest runtime flags validation passed.'
