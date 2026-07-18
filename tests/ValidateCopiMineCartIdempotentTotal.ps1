$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$ar = [regex]::Match($main, '(?s)def checkout_ar_cart_sync\(.*?def checkout_donation_cart_sync')
$donation = [regex]::Match($main, '(?s)def checkout_donation_cart_sync\(.*?def admin_add_ar_balance_sync')
if (-not $ar.Success -or -not $donation.Success) { throw 'Could not locate cart checkout implementations.' }
foreach ($block in @($ar.Value, $donation.Value)) {
  $replay = [regex]::Match($block, '(?s)if existing_rows:.*?(?=\r?\n\s*total_price\s*=)')
  if (-not $replay.Success -or $replay.Value -notmatch 'expected_total') {
    throw 'Cart idempotent replay must validate the expected total.'
  }
}
Write-Host 'CopiMine cart idempotent total contract OK'
