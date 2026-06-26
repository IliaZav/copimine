. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $economy 'hasOpenDonationEntitlement' 'EconomyCore must guard against duplicate active or unfinished donation entitlements.'
Require-Contains $economy "status IN ('UNCLAIMED','RESERVED','DELIVERING','DELIVERY_REVIEW')" 'EconomyCore duplicate-entitlement guard must include unfinished claim statuses.'
Require-Contains $economy "status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')" 'EconomyCore duplicate-entitlement guard must include active and in-flight item instances.'
Require-Contains $mainPy 'donation_entitlement_conflict_sync' 'admin-web purchase flow must enforce the same duplicate-entitlement guard.'

Throw-IfErrors 'ValidateCopiMineDonationEntitlementUniqueness'
