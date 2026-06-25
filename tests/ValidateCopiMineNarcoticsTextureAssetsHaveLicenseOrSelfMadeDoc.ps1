$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$license = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\LICENSES_RESOURCEPACK.md')
$notice = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md')
foreach ($marker in @('self-made', 'generated inside the CopiMine project', 'No third-party narcotics overlay, font, or shader asset was embedded')) {
  if (($license + $notice) -notmatch [regex]::Escape($marker)) { throw "Asset origin marker missing: $marker" }
}
Write-Host 'Texture asset origin documentation validation passed.'
