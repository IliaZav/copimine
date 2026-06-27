$ErrorActionPreference = "Stop"

$root = "D:/Desktop/Copimine/opt/copimine"
$siteRenderJs = Join-Path $root "admin-web/frontend/assets/js/public/site-render.js"
$donationPagesJs = Join-Path $root "admin-web/frontend/assets/js/player/donation-pages.js"

$siteRenderContent = Get-Content -Raw -Path $siteRenderJs
$donationPagesContent = Get-Content -Raw -Path $donationPagesJs

function Assert-Contains($content, $needle, $label) {
  if ($content -notmatch [regex]::Escape($needle)) {
    throw "$label is missing required fragment: $needle"
  }
}

function Assert-NotContains($content, $needle, $label) {
  if ($content -match [regex]::Escape($needle)) {
    throw "$label still contains forbidden fragment: $needle"
  }
}

Assert-Contains $siteRenderContent 'routePublicCommerce("artifacts")' "site-render.js"
Assert-Contains $siteRenderContent 'routePublicCommerce("donation-shop")' "site-render.js"
Assert-NotContains $siteRenderContent 'routePublicCommerce("#artifacts")' "site-render.js"
Assert-NotContains $siteRenderContent 'routePublicCommerce("#donation-shop")' "site-render.js"

Assert-Contains $donationPagesContent 'appRouteHref("donation-balance", { session: state.donationSessionId })' "donation-pages.js"
Assert-NotContains $donationPagesContent '/#donation-balance?session=' "donation-pages.js"

Write-Host "ValidateCopiMineWebCabinetCommerceLinksNoHash passed."
