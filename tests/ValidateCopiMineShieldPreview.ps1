$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$syncScript = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/scripts/sync_item_visuals.py')
$mapping = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks/item_texture_sources.json') | ConvertFrom-Json

if ($syncScript -match [regex]::Escape('def shield_preview(')) {
  throw 'Website visual sync must not generate a custom shield preview.'
}
if (@($mapping.items | Where-Object { $_.id -eq 'ne_segodnya_suka_shield' }).Count -ne 0) {
  throw 'The vanilla shield must not be included in website custom-texture mappings.'
}

Write-Host 'Vanilla shield website visual contract passed.'
