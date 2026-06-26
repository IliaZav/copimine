. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$items = Read-Utf8 (Join-Path $root 'copimine-artifacts\items.yml')

Require-Contains $items 'source: AR_SHOP' 'AR catalog items must explicitly declare AR_SHOP source.'
Require-Contains $artifacts 'private boolean isArCatalogItem(String itemId)' 'Artifacts must have an explicit AR catalog classifier.'
Require-Contains $artifacts '"AR_SHOP_ITEM"' 'Artifacts must mark official AR items with dedicated item type PDC.'
Require-Contains $artifacts '"AR_SHOP"' 'Artifacts must mark official AR items with dedicated source PDC.'
Require-Contains $artifacts '"DONATION_SHOP_ITEM"' 'Artifacts must keep donation items on their own PDC contract.'
Require-Contains $artifacts '"DONATION_SHOP"' 'Artifacts must keep donation source separated from AR source.'
Require-Contains $artifacts 'player.sendMessage(color("&cDonation-' 'Artifacts repair flows must explicitly reject donation items in AR repair.'

Throw-IfErrors 'ValidateCopiMineArtifactsArSeparatedFromDonation'
