$ErrorActionPreference = "Stop"

$root = "D:/Desktop/Copimine/opt/copimine"
$publicPageJs = Join-Path $root "admin-web/frontend/assets/js/public/public-page.js"
$homepageJs = Join-Path $root "admin-web/frontend/assets/js/public/homepage.js"
$siteDataJs = Join-Path $root "admin-web/frontend/assets/js/public/site-data.js"

$indexHtml = Join-Path $root "admin-web/frontend/index.html"
$serverHtml = Join-Path $root "admin-web/frontend/server.html"
$shopsHtml = Join-Path $root "admin-web/frontend/shops.html"
$modsHtml = Join-Path $root "admin-web/frontend/mods.html"

$publicPageContent = Get-Content -Raw -Path $publicPageJs
$homepageContent = Get-Content -Raw -Path $homepageJs
$siteDataContent = Get-Content -Raw -Path $siteDataJs

function Assert-Contains($content, $needle, $label) {
  if ($content -notmatch [regex]::Escape($needle)) {
    throw "$label is missing required fragment: $needle"
  }
}

Assert-Contains $publicPageContent "loadPublicPage(kind)" "public-page.js"
Assert-Contains $homepageContent "function resolvePublicPageKind()" "homepage.js"
Assert-Contains $homepageContent 'case "public-server"' "homepage.js"
Assert-Contains $homepageContent 'case "public-shops"' "homepage.js"
Assert-Contains $homepageContent 'case "public-mods"' "homepage.js"
Assert-Contains $siteDataContent "loadPublicHomePageData" "site-data.js"
Assert-Contains $siteDataContent "loadPublicServerPageData" "site-data.js"
Assert-Contains $siteDataContent "loadPublicShopsPageData" "site-data.js"
Assert-Contains $siteDataContent "loadPublicModsPageData" "site-data.js"

$htmlChecks = @(
  @{ Path = $indexHtml; Marker = 'data-page-kind="public-home"' },
  @{ Path = $serverHtml; Marker = 'data-page-kind="public-server"' },
  @{ Path = $shopsHtml; Marker = 'data-page-kind="public-shops"' },
  @{ Path = $modsHtml; Marker = 'data-page-kind="public-mods"' }
)

foreach ($check in $htmlChecks) {
  $html = Get-Content -Raw -Path $check.Path
  Assert-Contains $html $check.Marker ([System.IO.Path]::GetFileName($check.Path))
}

Write-Host "ValidateCopiMineWebPublicPagesSeparateRuntime passed."
