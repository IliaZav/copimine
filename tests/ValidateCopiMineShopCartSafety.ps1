$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$cartStore = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/shop-cart.js')
$cartPage = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/cart-page.js')
$renderer = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/site-render.js')
$siteData = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/public/site-data.js')

$required = @(
  @{ source = $cartStore; value = 'function scopedStorageKey()'; name = 'per-account cart storage key' },
  @{ source = $cartStore; value = 'export function setShopCartScope'; name = 'cart account scope setter' },
  @{ source = $cartPage; value = 'setShopCartScope(accountId ? `player-${accountId}` : "guest")'; name = 'cart account scope initialization' },
  @{ source = $cartPage; value = 'expected_total: expectedTotal'; name = 'server price confirmation' },
  @{ source = $cartPage; value = 'expected_total: expectedTotal'; name = 'price refresh feedback' },
  @{ source = $siteData; value = 'accountId: String(session.account?.id || "")'; name = 'player account identity' },
  @{ source = $renderer; value = '"UNCLAIMED", "RESERVED", "DELIVERING", "DELIVERY_REVIEW"'; name = 'live donation claim statuses' },
  @{ source = $renderer; value = '"ACTIVE", "DELIVERING", "PENDING_DELIVERY"'; name = 'live item instance statuses' }
)

$missing = @($required | Where-Object { $_.source -notmatch [regex]::Escape($_.value) } | ForEach-Object { $_.name })
if ($missing.Count -gt 0) {
  throw "Shop cart safety checks are incomplete: $($missing -join ', ')"
}

Write-Host 'ValidateCopiMineShopCartSafety passed.'
