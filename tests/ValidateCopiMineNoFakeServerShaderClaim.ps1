$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')

if ($runtime -match 'supportsShaderRuntime\(\)\s*\{\s*return false;\s*\}') {
  throw 'Server runtime must not use a hardcoded false stub for shader capability.'
}
foreach ($needle in @(
  'public boolean supportsShaderRuntime()',
  'detectTrueShaderSupport()',
  'CopiMineClient uses built-in ZIP shaderpacks through Iris when available',
  'server falls back to light particles only'
)) {
  if ($runtime -notmatch [regex]::Escape($needle)) {
    throw "Runtime honesty marker missing: $needle"
  }
}
foreach ($needle in @('"true_shader_runtime_supported": false','"server_forceable_shader_supported": false')) {
  if ($manifest -notmatch [regex]::Escape($needle)) { throw "Manifest honesty marker missing: $needle" }
}
Write-Host 'No-fake-server-shader-claim validation passed.'
