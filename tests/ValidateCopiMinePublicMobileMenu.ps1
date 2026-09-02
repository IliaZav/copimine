$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root 'admin-web\frontend\assets\js\public\public-nav.js'
$text = Get-Content -LiteralPath $script -Raw -Encoding UTF8
if (-not $text.Contains('expanded ? CLOSE_MENU_LABEL : OPEN_MENU_LABEL')) {
  throw 'Public mobile navigation must keep its accessible label synchronized with aria-expanded.'
}
foreach ($pageName in @('index.html', 'shops.html', 'server.html', 'cart.html')) {
  $page = Join-Path $root "admin-web\frontend\$pageName"
  $html = Get-Content -LiteralPath $page -Raw -Encoding UTF8
  if ($html -notmatch 'public-page\.js\?v=[0-9A-Za-z_-]+') { throw "Public module cache-buster missing: $pageName" }
}
$legacyMods = Get-Content -LiteralPath (Join-Path $root 'admin-web\frontend\mods.html') -Raw -Encoding UTF8
$legacyRedirect = Get-Content -LiteralPath (Join-Path $root 'admin-web\frontend\assets\js\public\legacy-mods-redirect.js') -Raw -Encoding UTF8
if ($legacyMods -notmatch 'legacy-mods-redirect\.js' -or $legacyRedirect -notmatch "window\.location\.replace\('/launcher\.html'\)") {
  throw 'Legacy mods page must keep its Launcher compatibility redirect.'
}
Write-Host 'CopiMine public mobile menu contract OK'
