$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')

# Narcotics screen shaders are retired: the server must report no shader capability at all.
if ($runtime -notmatch '(?s)public boolean supportsShaderRuntime\(\)\s*\{.{0,120}?return false;') {
  throw 'Server runtime must report shader support as unavailable.'
}
if ($runtime -notmatch '(?s)public boolean supportsClientZipShaderpackRuntime\(\)\s*\{.{0,120}?return false;') {
  throw 'Server runtime must report ZIP shaderpack support as unavailable.'
}
foreach ($needle in @('narcotics screen shaders retired', 'server title overlay retired')) {
  if ($runtime -notmatch [regex]::Escape($needle)) {
    throw "Runtime honesty marker missing: $needle"
  }
}
foreach ($needle in @('"true_shader_runtime_supported": false','"server_forceable_shader_supported": false')) {
  if ($manifest -notmatch [regex]::Escape($needle)) { throw "Manifest honesty marker missing: $needle" }
}
Write-Host 'No-fake-server-shader-claim validation passed.'