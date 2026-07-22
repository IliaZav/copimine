$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'admin-web\frontend\assets\js\public\cart-page.js'
$source = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)

if ($source -notmatch 'String\(item\?\.item_id\s*\|\|\s*""\)\.trim\(\)\.toLowerCase\(\)\s*===\s*itemId') {
  throw 'Cart catalog lookup must normalize backend item ids before matching cart rows.'
}

Write-Output 'ValidateCopiMineShopCartCatalogCase passed.'
