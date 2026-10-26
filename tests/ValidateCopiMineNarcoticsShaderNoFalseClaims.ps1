$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
foreach ($marker in @('"shader_supported": false','narcotics screen shaders retired')) {
  if (($runtime + $manifest) -notmatch [regex]::Escape($marker)) { throw "Retired-shader guard marker missing: $marker" }
}
if ($runtime -match 'sendVisualStart|sendVisualRefresh|requestedClientShaderpack') {
  throw 'Retired narcotics shaders must not leave an active invocation path.'
}
Write-Host 'Narcotics shader false-claim validation passed.'