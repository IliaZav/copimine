$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$economySourcePath = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'
$economySource = Get-Content -LiteralPath $economySourcePath -Raw -Encoding UTF8
$errors = [System.Collections.Generic.List[string]]::new()

if ($source -notmatch [regex]::Escape('VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)')) {
    $errors.Add('The revenue-payout insert must bind all 15 declared columns; otherwise a successful AR transfer is rolled back by a false database error.')
}
if ($source -match [regex]::Escape("VALUES(?,?,?,?,?,?,?,?,?,?,'',?,?,?,?)")) {
    $errors.Add('The revenue-payout insert still contains the old 14-placeholder layout that shifts bank transaction and timestamp values.')
}
if ($source -notmatch [regex]::Escape('var8.setString(11, var3.txId());')) {
    $errors.Add('The revenue-payout record must retain the matching EconomyCore transfer id for idempotent reconciliation.')
}

$hints = [regex]::Match($source, '(?s)private void tickPendingHints\(\) \{.*?(?=\r?\n\s*private CopiMineArtifacts\.Shop currentShop)')
if (-not $hints.Success -or $hints.Value -notmatch 'pendingCountsForPlayers\(') {
    $errors.Add('Pending-delivery hints must batch the online-player count query instead of submitting one database query per player.')
}
if ($hints.Success -and $hints.Value -match 'for \(Player .*runAsync\(') {
    $errors.Add('Pending-delivery hints still submit a separate asynchronous database task for every online player.')
}

$snare = [regex]::Match($source, '(?s)private void applyTemporaryCobwebSnare\(.*?\) \{.*?(?=\r?\n\s*private void healPlayerCapped)')
if (-not $snare.Success) {
    $errors.Add('Could not locate the temporary cobweb snare implementation.')
} else {
    foreach ($marker in @('Player', 'BlockPlaceEvent', 'Bukkit.getPluginManager().callEvent', 'isCancelled()', 'canBuild()')) {
        if ($snare.Value -notmatch [regex]::Escape($marker)) {
            $errors.Add("Cobweb snare must consult the normal protected block-placement path before changing terrain (missing: $marker).")
        }
    }
}
if ($source -notmatch [regex]::Escape('this.applyTemporaryCobwebSnare(var2, var10, 100L);')) {
    $errors.Add('The combat handler must pass the attacking player to the cobweb protection check.')
}

$delivery = [regex]::Match($source, '(?s)private void deliverPurchase\(Player var1, CopiMineArtifacts\.PurchaseContext var2, CopiMineArtifacts\.BridgeTxnResult var3\) \{.*?(?=\r?\n\s*private void executeRepair)')
if (-not $delivery.Success -or $delivery.Value -notmatch '(?s)if \(!var1\.isOnline\(\)\) \{.*?createPendingDelivery\(var1, var2\).*?return;') {
    $errors.Add('A player disconnecting after a successful charge must be moved to pending delivery before any physical inventory write is attempted.')
}

foreach ($marker in @(
    'scheduleOrphanedShopTransferReconciliation(',
    'readOrphanedShopTransfers(',
    'recordFetchedPage(fetchedCursors, 128)',
    'pendingSnapshot().entrySet()',
    'artifact-purchase-',
    'ArtifactShopPurchaseRecoveryGuard.refundIdempotencyKey(',
    'AR_SHOP_PURCHASE_RECOVERY_REFUND',
    'ArtifactRevenuePayoutPolicy.isWithinOrphanRefundGracePeriod(transfer.createdAt(), this.now())',
    'ArtifactRevenuePayoutPolicy.purchasePersistenceLockKey(purchaseId)',
    'lockArtifactPurchasePersistence(var4, var2.purchaseId())',
    'SET LOCAL lock_timeout = ''5s''',
    'beginNextScan(60_000L)',
    'completedScanPolls < 6',
    'runTaskTimerAsynchronously(this, 20L, 200L)'
)) {
    if ($source -notmatch [regex]::Escape($marker)) {
        $errors.Add("Orphaned shop-transfer recovery must page by a durable cursor, retry transient refunds, and reverse an unpersisted purchase after interruption (missing: $marker).")
    }
}
if ($source -match 'attempts\s*>\s*60') {
    $errors.Add('Orphaned shop-transfer recovery must keep retrying after its first bounded scan instead of stopping after five minutes.')
}
$reconciliation = [regex]::Match($source, '(?s)private void scheduleOrphanedShopTransferReconciliation\(\) \{.*?(?=\r?\n\s*private boolean refundOrSkipOrphanedShopTransfer)')
$disableCancellation = [regex]::Match($reconciliation.Value, '(?s)if \(!CopiMineArtifacts\.this\.isEnabled\(\)\) \{.*?\bcancel\s*\(\)')
$cancellationCount = [regex]::Matches($reconciliation.Value, '\bcancel\s*\(').Count
if (-not $reconciliation.Success -or -not $disableCancellation.Success -or $cancellationCount -ne 1) {
    $errors.Add('A completed orphan-transfer scan must leave the scheduled worker alive for later periodic scans.')
}
$refundRecovery = [regex]::Match($source, '(?s)private boolean refundOrSkipOrphanedShopTransfer\(.*?\{.*?(?=\r?\n\s*private boolean hasPersistedArtifactShopPurchase)')
$inFlightCheckIndex = $refundRecovery.Value.IndexOf('isPurchaseInFlight(purchaseId)', [StringComparison]::Ordinal)
$persistedLookupIndex = $refundRecovery.Value.IndexOf('hasPersistedArtifactShopPurchase(idempotencyKey)', [StringComparison]::Ordinal)
if (-not $refundRecovery.Success -or $inFlightCheckIndex -lt 0 -or $persistedLookupIndex -lt 0 -or $inFlightCheckIndex -ge $persistedLookupIndex) {
    $errors.Add('Recovery must check the purchase in-flight guard before reading persisted purchase state.')
}
$rollingCursorPredicate = 'AND (t.created_at > ? OR (t.created_at = ? AND (? = '''' OR t.tx_id > ?)))'
if (-not $economySource.Contains($rollingCursorPredicate)) {
    $errors.Add('Periodic orphan-transfer scans must honor the timestamp cursor when replaying a window with an empty transaction id.')
}

if ($errors.Count -gt 0) {
    throw ("Artifacts shop reliability validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Artifacts shop reliability validation passed.'
