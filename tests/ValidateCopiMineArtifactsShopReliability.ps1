$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
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
if ($source -notmatch [regex]::Escape('this.applyTemporaryCobwebSnare(var2, var10);')) {
    $errors.Add('The combat handler must pass the attacking player to the cobweb protection check.')
}

$delivery = [regex]::Match($source, '(?s)private void deliverPurchase\(Player var1, CopiMineArtifacts\.PurchaseContext var2, CopiMineArtifacts\.BridgeTxnResult var3\) \{.*?(?=\r?\n\s*private void executeRepair)')
if (-not $delivery.Success -or $delivery.Value -notmatch '(?s)if \(!var1\.isOnline\(\)\) \{.*?createPendingDelivery\(var1, var2\).*?return;') {
    $errors.Add('A player disconnecting after a successful charge must be moved to pending delivery before any physical inventory write is attempted.')
}

foreach ($marker in @('reconcileOrphanedShopTransfers(', 'readOrphanedShopTransfers(', 'artifact-purchase-', 'artifact-orphan-refund-', 'AR_SHOP_PURCHASE')) {
    if ($source -notmatch [regex]::Escape($marker)) {
        $errors.Add("A startup reconciliation path must reverse a completed shop transfer that has no persisted artifact order after an interruption (missing: $marker).")
    }
}

if ($errors.Count -gt 0) {
    throw ("Artifacts shop reliability validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Artifacts shop reliability validation passed.'
