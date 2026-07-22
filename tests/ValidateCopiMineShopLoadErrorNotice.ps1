$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot '..\admin-web\frontend\assets\js\cabinet-runtime.js'
$source = Get-Content -Raw -Encoding UTF8 $path
if ($source -notmatch 'const shopLoadErrors = \[') {
  throw 'loadShops must collect endpoint errors instead of silently rendering empty tables.'
}
if ($source -notmatch 'shopLoadErrors\.length \?') {
  throw 'loadShops must show a visible error notice when an endpoint fails.'
}
Write-Output 'ValidateCopiMineShopLoadErrorNotice passed.'
