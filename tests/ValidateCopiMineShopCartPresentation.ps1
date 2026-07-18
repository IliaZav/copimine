$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$shops = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/shops.html')
$cart = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/cart.html') -ErrorAction SilentlyContinue
$renderer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/site-render.js')
$cartModule = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/shop-cart.js') -ErrorAction SilentlyContinue
$required = @(
  @{ source = $shops; value = 'id="shopCartButton"'; name = 'shop header cart button' },
  @{ source = $shops; value = 'href="/cart.html"'; name = 'cart page link' },
  @{ source = $cart; value = 'data-page-kind="public-cart"'; name = 'cart page' },
  @{ source = $cart; value = 'id="arCartSection"'; name = 'AR cart section' },
  @{ source = $cart; value = 'id="donationCartSection"'; name = 'donation cart section' },
  @{ source = $renderer; value = 'addShopCartItem'; name = 'catalog add-to-cart action' },
  @{ source = $renderer; value = 'image_url'; name = 'item texture rendering' },
  @{ source = $cartModule; value = 'shopCartChanged'; name = 'cart count updates' }
)

$missing = @($required | Where-Object { $_.source -notmatch [regex]::Escape($_.value) } | ForEach-Object { $_.name })
if ($missing.Count -gt 0) {
  throw "Shop cart presentation is incomplete: $($missing -join ', ')"
}

if ($renderer -match 'meta\.append\(makeElement\("span", "", material\)\)') {
  throw 'Visible shop cards must not render raw Minecraft material identifiers.'
}

Write-Host 'ValidateCopiMineShopCartPresentation passed.'
