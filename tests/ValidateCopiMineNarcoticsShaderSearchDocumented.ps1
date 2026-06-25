$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$doc = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md')
foreach ($marker in @('Search summary','Reviewed sources','Decision','Minecraft Wiki','OpenGameArt')) {
  if ($doc -notmatch [regex]::Escape($marker)) { throw "Shader search documentation marker missing: $marker" }
}
Write-Host 'Narcotics shader search documentation validation passed.'
