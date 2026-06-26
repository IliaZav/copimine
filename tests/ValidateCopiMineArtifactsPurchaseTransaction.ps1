$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('persistPaidPurchase','setAutoCommit(false)','commit()','rollback()','createPendingDelivery','markPurchaseDelivered','bridge.refund','artifact-purchase-','PAID','PENDING_DELIVERY')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing purchase transaction marker: $marker") }
}
foreach ($marker in @('lockArtifactPurchaseConstraints(','ARTIFACT_LIMIT_SUPPLY','ARTIFACT_LIMIT_PLAYER','markArtifactPurchaseCancelled(','markArtifactPurchaseReview(','purchase_manual_review','repair_manual_review')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing hardening marker: $marker") }
}
if ($text -notmatch '(?s)persistPaidPurchase\(Player player, PurchaseContext context, BridgeTxnResult charge\).*?lockArtifactPurchaseConstraints\(c, player\.getUniqueId\(\)\.toString\(\), context\.item\(\)\.itemId\(\)\);.*?purchasedCount\(c, context\.item\(\)\.itemId\(\)\).*?playerPurchasedCount\(c, player\.getUniqueId\(\)\.toString\(\), context\.item\(\)\.itemId\(\)\)' ) {
  $errors.Add('persistPaidPurchase must re-check supply/per-player limits inside the SQL transaction after acquiring purchase locks.')
}
if ($text -notmatch '(?s)deliverPurchase\(Player player, PurchaseContext context, BridgeTxnResult charge\).*?markArtifactPurchaseReview\(context\.purchaseId\(\), context\.uniqueItemId\(\)\);.*?finalize_after_physical_delivery_failed' ) {
  $errors.Add('Direct physical issuance must move artifact purchases into DELIVERY_REVIEW when DB finalization fails.')
}
if ($text -notmatch '(?s)createPendingDelivery\(player, context\).*?BridgeTxnResult refund = bridge\.refund\(player, context\.item\(\)\.priceAr\(\), "artifact-pending-refund-".*?markArtifactPurchaseCancelled\(context\.purchaseId\(\), context\.uniqueItemId\(\)\).*?markArtifactPurchaseReview\(context\.purchaseId\(\), context\.uniqueItemId\(\)\)' ) {
  $errors.Add('Pending-delivery failures must either cancel/refund cleanly or move the purchase into DELIVERY_REVIEW.')
}
if ($text -notmatch '(?s)executeRepair\(Player player, long price\).*?BridgeTxnResult refund = bridge\.refund\(player, price, "artifact-repair-refund-".*?repair_manual_review' ) {
  $errors.Add('Repair rollback path must check refund result and raise manual review on failure.')
}
if ($errors.Count -gt 0) { throw ("Artifacts purchase transaction validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts purchase transaction validation passed.'
