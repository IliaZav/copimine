. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$java = Read-Utf8 $Paths.Admin
$py = Read-Utf8 $Paths.MainPy
$frontend = Read-FrontendBundle
$styles = Read-FrontendStyles

foreach ($marker in @(
  'cmv7_ar_assets',
  'cmv7_ar_transactions',
  'idx_cmv7_ar_assets_owner',
  'idx_cmv7_ar_transactions_time',
  'ensureArAsset',
  'retagArOwner',
  'recordArTransaction',
  'AR_TRANSFER_PICKUP',
  'AR_SMELT_DIAMOND',
  'AR_DROP_LISTED',
  'EntityDamageEvent',
  'ItemDespawnEvent',
  'ItemMergeEvent',
  'InventoryPickupItemEvent',
  'InventoryMoveItemEvent',
  'BlockDispenseEvent',
  'UNCERTIFIED_AR_SMELT_BLOCKED'
)) {
  Require-Contains $java $marker "AdminPlus AR guard marker missing: $marker"
}

Require-Regex $java 'BlockPlaceEvent[\s\S]{0,900}setCancelled\(true\)' 'AR place guard cancellation must stay active.'
Require-Regex $java 'FurnaceSmeltEvent[\s\S]{0,900}setResult\(new ItemStack\(Material\.DIAMOND' 'Certified AR smelting output must be explicit.'
Require-Regex $java '!isOfficialArItem\(it\)' 'Death handling must keep AR droppable without deleting normal items.'

foreach ($marker in @(
  'cmv7_ar_transactions',
  'cmv7_ar_assets',
  '"transactions"',
  '"assets"',
  '"transfers"',
  '"smelts"'
)) {
  Require-Contains $py $marker "Backend AR ledger marker missing: $marker"
}

Require-Contains $frontend 'ledger.transactions' 'Frontend must read AR transactions from the ledger API.'
Require-Contains $frontend 'table("economy-transactions-table"' 'Frontend must render the AR transaction table.'
Require-Contains $styles '.economy-transactions' 'CSS must style AR transaction sections.'
Require-Contains $styles '.economy-assets' 'CSS must style AR asset sections.'

Throw-IfErrors 'ValidateCopiMineArEconomyReleaseGuards'
