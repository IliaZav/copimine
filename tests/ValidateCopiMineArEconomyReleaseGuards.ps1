$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$backend = Join-Path $root "admin-web\backend\main.py"
$frontend = Join-Path $root "admin-web\frontend\assets\app.js"

$failures = New-Object System.Collections.Generic.List[string]

function Require-File([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Missing file: $path")
        return $false
    }
    return $true
}

function Require-Text([string]$name, [string]$text, [string]$pattern) {
    if ($text -notmatch $pattern) {
        $failures.Add("$name is missing pattern: $pattern")
    }
}

if (Require-File $plugin) { $java = Get-Content -LiteralPath $plugin -Raw -Encoding UTF8 } else { $java = "" }
if (Require-File $backend) { $py = Get-Content -LiteralPath $backend -Raw -Encoding UTF8 } else { $py = "" }
if (Require-File $frontend) { $js = Get-Content -LiteralPath $frontend -Raw -Encoding UTF8 } else { $js = "" }

Require-Text "plugin DB schema" $java "cmv7_ar_assets"
Require-Text "plugin DB schema" $java "cmv7_ar_transactions"
Require-Text "plugin DB index" $java "idx_cmv7_ar_assets_owner"
Require-Text "plugin DB index" $java "idx_cmv7_ar_transactions_time"
Require-Text "plugin DB index" $java "idx_cmv7_ar_transactions_asset"

Require-Text "AR certified asset registry" $java "ensureArAsset"
Require-Text "AR ownership transfer" $java "retagArOwner"
Require-Text "AR transaction ledger" $java "recordArTransaction"
Require-Text "AR pickup transfer event" $java "AR_TRANSFER_PICKUP"
Require-Text "AR smelt transaction" $java "AR_SMELT_DIAMOND"
Require-Text "AR drop transaction" $java "AR_DROP_LISTED"

Require-Text "AR entity damage guard" $java "EntityDamageEvent"
Require-Text "AR entity damage guard" $java "setFireTicks\(0\)"
Require-Text "AR despawn guard" $java "ItemDespawnEvent"
Require-Text "AR merge guard" $java "ItemMergeEvent"
Require-Text "AR hopper pickup guard" $java "InventoryPickupItemEvent"
Require-Text "AR hopper/dispenser move guard" $java "InventoryMoveItemEvent"
Require-Text "AR dispenser guard" $java "BlockDispenseEvent"
Require-Text "AR place guard" $java "AR_PLACE_BLOCKED"
Require-Text "AR place guard cancellation" $java "BlockPlaceEvent[\s\S]{0,600}setCancelled\(true\)"

Require-Text "AR smelting output" $java "FurnaceSmeltEvent"
Require-Text "AR smelting output" $java "setResult\(new ItemStack\(Material\.DIAMOND"
Require-Text "uncertified AR smelting guard" $java "UNCERTIFIED_AR_SMELT_BLOCKED"

Require-Text "death keeps AR droppable" $java "!isOfficialArItem\(it\)"
Require-Text "admin economy custody GUI" $java "open:ar-custody"
Require-Text "admin economy custody GUI" $java "openArCustody"

Require-Text "backend transaction rows" $py "cmv7_ar_transactions"
Require-Text "backend asset rows" $py "cmv7_ar_assets"
Require-Text "backend transaction response" $py '"transactions"\s*:'
Require-Text "backend asset response" $py '"assets"\s*:'
Require-Text "backend summary transfers" $py '"transfers"\s*:'
Require-Text "backend summary smelts" $py '"smelts"\s*:'

Require-Text "frontend transaction panel" $js "economy-transactions"
Require-Text "frontend transaction data" $js "ledger\.transactions"
Require-Text "frontend asset registry" $js "ledger\.assets"
Require-Text "frontend transaction class" $js "economy-transactions"
Require-Text "frontend asset class" $js "economy-assets"

if ($failures.Count -gt 0) {
    Write-Host "AR economy release guard validation FAILED:" -ForegroundColor Red
    foreach ($failure in $failures) { Write-Host " - $failure" -ForegroundColor Red }
    exit 1
}

Write-Host "AR economy release guard validation passed." -ForegroundColor Green
