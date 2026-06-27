. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts '.transferToAccount(' 'AR shop/repair flows must route funds through an explicit recipient transfer, not silent burn logic.'
Require-Contains $artifacts 'PRESIDENT_BUDGET_ACCOUNT_ID' 'AR shop/repair flows must point to a concrete treasury recipient instead of deleting AR.'
Require-Contains $artifacts '"AR_SHOP_PURCHASE"' 'AR purchases must stay typed and auditable.'
Require-Contains $artifacts '"AR_ITEM_REPAIR"' 'AR repairs must stay typed and auditable.'
Require-Regex $artifacts 'if \(!isArCatalogItem\(catalog\.itemId\(\)\)\) \{' 'Artifacts repair flow must hard-block non-AR items instead of treating them as generic burnable repair targets.'

Throw-IfErrors 'ValidateCopiMineArNeverBurned'
