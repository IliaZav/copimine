. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$economy = Read-Utf8 $Paths.Economy

Require-Contains $artifacts "CREATE UNIQUE INDEX IF NOT EXISTS ux_artifact_instances_owner_item_live ON artifact_item_instances(owner_uuid,item_id) WHERE status IN ('ACTIVE','DELIVERING','PENDING_DELIVERY')" 'Artifacts must enforce a DB-level live unique index for owner-bound donation instances.'
Require-Contains $artifacts 'lockDonationInstanceEntitlement(c, ownerUuid, itemId);' 'Artifacts must take an entitlement advisory lock before persisting donation delivery instances.'
Require-Contains $artifacts 'lockDonationInstanceEntitlement(c, ownerUuid, row.itemId());' 'Artifacts must lock reclaim entitlement before issuing a replacement instance.'
Require-Contains $economy 'lockDonationEntitlement(connection, playerUuidText, normalized);' 'EconomyCore must lock donation entitlements before creating claims.'
Require-Contains $economy 'if (hasOpenDonationEntitlement(connection, playerUuidText, normalized))' 'EconomyCore must reject duplicate open entitlements before inserting a new claim.'
Require-Contains $economy 'if (normalizedAmount != 1L)' 'EconomyCore must reject multi-amount owner-bound donation claims.'

Throw-IfErrors 'ValidateCopiMineDonationClaimSingleInstanceOnly'
