$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_visuals_manifest.json')
$notice = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md')
if ($manifest -match '"shader_supported"\s*:\s*false') {
  if ($manifest -notmatch [regex]::Escape('Paper server cannot reliably trigger client post-processing shaders')) {
    throw 'Shader unsupported state is not explained in the visuals manifest.'
  }
  if ($notice -notmatch [regex]::Escape('cannot reliably force true client post-processing shaders')) {
    throw 'Shader unsupported state is not documented in third-party notice.'
  }
} else {
  Write-Warning 'Shader runtime is marked supported; manual verification required.'
}
Write-Host 'Shader runtime support/documentation validation passed.'
