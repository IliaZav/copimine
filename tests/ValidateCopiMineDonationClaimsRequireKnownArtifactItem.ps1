. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $economy 'normalizeDonationItemId(itemId);' 'EconomyCore must reject unknown donation claim item ids before row creation.'
Require-Contains $economy 'getMethod("knowsDonationCatalogItem", String.class)' 'EconomyCore must validate donation item ids against the donation-only Artifacts catalog bridge.'
Require-Contains $artifacts 'public boolean knowsDonationCatalogItem(String itemId)' 'Artifacts must expose donation-only catalog item validation for donation claims.'

Throw-IfErrors 'ValidateCopiMineDonationClaimsRequireKnownArtifactItem'
