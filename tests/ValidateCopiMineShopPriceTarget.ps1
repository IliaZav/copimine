$ErrorActionPreference = 'Stop'

$path = Join-Path $PSScriptRoot '..\admin-web\backend\main.py'
$source = Get-Content -Raw -Encoding UTF8 $path
foreach ($marker in @(
  '_normalize_shop_item_id',
  'for catalog_path in _shop_catalog_paths()',
  'matched_paths = 0',
  'error.status_code != 404',
  'detail='
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Missing price editor compatibility guard: $marker" }
}
Write-Host 'Shop price target validation passed.'
