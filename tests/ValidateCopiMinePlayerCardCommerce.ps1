$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$catalog = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\commerce_catalog.py')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @(
  'class AdminArtifactGiftIn',
  'def admin_create_artifact_gift_sync',
  '/api/admin/artifacts/gift',
  'require_sensitive_confirm(request, "ADMIN_ARTIFACT_GIFT")',
  'artifact_pending_deliveries',
  'donation_item_claims',
  'admin_gift_catalog_snapshot_sync',
  'admin-donation-gift-',
  'admin-artifact-gift-',
  'donation_entitlement_conflict_sync'
)) {
  if ($main -notmatch [regex]::Escape($marker)) { $errors.Add("Backend marker missing: $marker") }
}
$giftBody = [regex]::Match($main, '(?s)def admin_create_artifact_gift_sync\(.*?def admin_create_donation_test_purchase_sync')
if (-not $giftBody.Success) { $errors.Add('Could not isolate the administrative gift transaction.') }
elseif ($giftBody.Value -match 'UPDATE\s+(donation_accounts|cmv4_bank_accounts)') { $errors.Add('Administrative gift must not mutate a player balance.') }
elseif ($giftBody.Value -notmatch "price_ar[^\r\n]*0" -or $giftBody.Value -notmatch 'price_donation[^\r\n]*0') { $errors.Add('Administrative gift must persist a zero-price delivery.') }
foreach ($marker in @('def admin_gift_catalog_snapshot', 'source', 'ADMIN_ONLY')) {
  if ($catalog -notmatch [regex]::Escape($marker)) { $errors.Add("Catalog marker missing: $marker") }
}
foreach ($marker in @(
  'playerAdminArAddBalance',
  'playerAdminDonationAddBalance',
  'playerAdminGift',
  '/api/admin/shop/admin-gift-items',
  'ADMIN_ARTIFACT_GIFT'
)) {
  if ($runtime -notmatch [regex]::Escape($marker)) { $errors.Add("Player card marker missing: $marker") }
}

$giftBlock = [regex]::Match($runtime, '(?s)playerAdminGift.*?playerAdminGift')
if (-not $giftBlock.Success -or $runtime -notmatch 'data-click="playerAdminGift\(\x27\$\{esc\(player\)') {
  $errors.Add('Player card gift action must receive the selected player directly.')
}
if ($runtime -match 'playerAdminGift\([^\r\n]*playerSelectOptions') {
  $errors.Add('Player card gift action must not require a second player picker.')
}

if ($errors.Count) { throw ("Player card commerce validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'CopiMine player card commerce contract OK'
