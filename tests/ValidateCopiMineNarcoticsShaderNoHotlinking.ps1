$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$doc = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md')
foreach ($marker in @('No hotlinking','No runtime external downloads')) {
  if ($doc -notmatch [regex]::Escape($marker)) { throw "Missing shader safety marker: $marker" }
}
Write-Host 'Shader no-hotlinking validation passed.'
