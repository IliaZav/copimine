$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/backend/main.py')
$required = @(
  'class PlayerCartCheckoutIn(BaseModel):',
  'def checkout_ar_cart_sync(',
  'def checkout_donation_cart_sync(',
  '@app.post("/api/player/shop/cart/ar/checkout")',
  '@app.post("/api/player/shop/cart/donation/checkout")',
  'artifact_pending_deliveries',
  'donation_item_claims',
  'verify_bank_pin(',
  'expected_total',
  'artifact-purchase-supply:',
  'idempotency_key'
)

$missing = @($required | Where-Object { $main -notmatch [regex]::Escape($_) })
if ($missing.Count -gt 0) {
  throw "Shop cart contract is incomplete: $($missing -join ', ')"
}

Write-Host 'ValidateCopiMineShopCartContract passed.'
