$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json'
$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($manifest.shader_runtime.supported -eq $true) { throw 'Server manifest must not claim true shader runtime support.' }
if ($runtime -notmatch 'server title overlay retired: Narcotics only uses restrained normal particles') {
  throw 'Runtime must state that server overlays are retired in favor of restrained particles.'
}
if ($runtime -match 'sendVisualStart|sendVisualRefresh') {
  throw 'Narcotics must not ask the client to start or refresh a shader session.'
}
Write-Host 'No fake server shader claim validation passed.'
