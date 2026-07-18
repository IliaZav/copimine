$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$mapping = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks/item_texture_sources.json') | ConvertFrom-Json
$missing = [System.Collections.Generic.List[string]]::new()

foreach ($item in @($mapping.items)) {
  $id = [string]$item.id
  if ([string]::IsNullOrWhiteSpace($id)) {
    $missing.Add('<empty item id>')
    continue
  }
  $asset = Join-Path $root "admin-web/frontend/assets/item-textures/$id.png"
  if (-not (Test-Path -LiteralPath $asset)) {
    $missing.Add("$id (asset missing)")
    continue
  }
  if ((Get-Item -LiteralPath $asset).Length -lt 70) {
    $missing.Add("$id (asset is empty)")
  }
}

if ($missing.Count -gt 0) {
  throw "Website item visual coverage is incomplete: $($missing -join ', ')"
}

Write-Host 'ValidateCopiMineWebsiteItemVisuals passed.'
