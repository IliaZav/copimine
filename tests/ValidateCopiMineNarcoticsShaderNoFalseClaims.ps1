$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
if ($runtime -match 'supportsShaderRuntime\(\)\s*\{\s*return false;\s*\}') { throw 'supportsShaderRuntime is still a hardcoded false stub.' }
foreach ($marker in @('"shader_supported": false','client-side hook','manifestFlag("shader_supported")','detectShaderSupport()')) {
  if (($runtime + $manifest) -notmatch [regex]::Escape($marker)) { throw "False-claim guard marker missing: $marker" }
}
Write-Host 'Narcotics shader false-claim validation passed.'
