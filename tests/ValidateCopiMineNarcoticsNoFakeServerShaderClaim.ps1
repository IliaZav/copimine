$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json'
$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($manifest.shader_runtime.supported -eq $true) { throw 'Server manifest must not claim true shader runtime support.' }
if ($runtime -notmatch 'server title overlay disabled: CopiMine now uses Iris shaderpacks') { throw 'Runtime must state that server title overlays are disabled in favor of client shaderpacks/fallback.' }
Write-Host 'No fake server shader claim validation passed.'
