$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$economy = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('openAdminGiftPlayersAsync', 'openAdminGiftCatalog', 'openAdminGiftConfirm', 'ADMIN_GIFT', 'createAdminGiftAsync', 'admin_gift:', 'giftNotice')) {
  if ($artifacts -notmatch [regex]::Escape($marker) -and $economy -notmatch [regex]::Escape($marker)) { $errors.Add("Missing gift marker: $marker") }
}
if ($economy -match 'createAdminGiftAsync[\s\S]{0,1800}mutateDonationBalanceInConnection') { $errors.Add('Administrative donation gifts must not debit the donation balance.') }
if ($artifacts -notmatch 'FROM \(SELECT player_uuid' -or $artifacts -notmatch 'donation_purchases' -or $artifacts -notmatch 'artifact_item_instances') { $errors.Add('Offline player discovery query is missing.') }
if ($artifacts -notmatch 'adminGift\s*&&\s*!this\.isAdminOnlyCatalogItem\(item\.itemId\(\)\)') { $errors.Add('AR gift catalog must exclude admin-only entries.') }
if ($artifacts -notmatch 'this\.isPurchasableDonationItem\(item\.itemId\(\)\)') { $errors.Add('Donation gift catalog must use the purchasable-item gate.') }
if ($artifacts -notmatch 'isRetiredArtifact') { $errors.Add('Gift flow must honor retired-item filtering.') }
if ($errors.Count) { throw ("Admin gift validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineAdminGiftFlow passed.'
