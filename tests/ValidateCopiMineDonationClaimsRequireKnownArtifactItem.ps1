. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $economy 'requireKnownDonationItem(itemId);' 'EconomyCore must reject unknown donation claim item ids before row creation.'
Require-Contains $economy 'getMethod("knowsCatalogItem", String.class)' 'EconomyCore must validate donation item ids against the Artifacts catalog bridge.'
Require-Contains $artifacts 'public boolean knowsCatalogItem(String itemId)' 'Artifacts must expose catalog item validation for donation claims.'

Throw-IfErrors 'ValidateCopiMineDonationClaimsRequireKnownArtifactItem'
