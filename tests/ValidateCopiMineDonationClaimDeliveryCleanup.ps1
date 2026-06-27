. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'removeOfficialItemsFromInventory(PlayerInventory inventory, Collection<String> uniqueItemIds)' 'Artifacts must provide a cleanup helper for partially inserted donation items.'
Require-Regex $artifacts 'removeOfficialItemsFromInventory\([^,]+\.getInventory\(\),\s*[^)]+\);' 'Artifacts must clean up already inserted donation items if claim delivery starts failing mid-issue.'
Require-Regex $artifacts 'failDonationDeliveryAsync\([^,]+\.getUniqueId\(\),\s*[^,]+\.claimId\(\),\s*[^)]+\);' 'Artifacts must move ambiguous donation deliveries to DELIVERY_REVIEW/invalid cleanup flow instead of silently retrying.'

Throw-IfErrors 'ValidateCopiMineDonationClaimDeliveryCleanup'
