. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $economy 'private long resolveDonationCatalogPrice(String itemId)' 'EconomyCore must resolve donation prices from the catalog bridge.'
Require-Contains $economy 'long catalogPrice = resolveDonationCatalogPrice(normalized);' 'EconomyCore test purchase flow must use catalog pricing instead of caller-supplied price.'
Require-Contains $economy 'long price = resolveDonationCatalogPrice(normalized);' 'EconomyCore purchase intent must use catalog pricing instead of caller-supplied price.'
Require-Contains $mainPy 'catalog = donation_catalog_snapshot_sync()' 'Admin donation test purchase must load the backend donation catalog.'
Require-Contains $mainPy 'price = int(item.get("price_donation") or 0)' 'Admin donation test purchase must take price from the backend catalog.'
Require-NotContains $mainPy '"price": int(data.price)' 'Admin donation test purchase must not echo untrusted request price into the audit payload.'

Throw-IfErrors 'ValidateCopiMineDonationPurchaseIntentBackendPriced'
