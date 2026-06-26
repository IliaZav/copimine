. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$items = Read-Utf8 (Join-Path $root 'copimine-artifacts\items.yml')

Require-Contains $items 'reclaim-policy: LOSS_ONLY' 'Donation catalog entries must declare LOSS_ONLY reclaim policy.'
Require-Contains $artifacts 'status=''LOST_RECLAIMABLE''' 'Artifacts reclaim flow must read only LOST_RECLAIMABLE instances.'
Require-Contains $artifacts '!"LOSS_ONLY".equalsIgnoreCase(firstNonBlank(donation.reclaimPolicy(), "LOSS_ONLY"))' 'Artifacts must reject reclaim when donation catalog policy is not LOSS_ONLY.'
Require-Contains $artifacts 'status=''REPLACED_AFTER_LOSS''' 'Artifacts reclaim flow must retire the old instance with REPLACED_AFTER_LOSS.'
Require-Contains $artifacts 'updateDonationInstanceStatus(ref.ownerUuid(), ref.uniqueItemId(), ref.itemId(), "BROKEN"' 'Artifacts must keep BROKEN status separate from reclaimable loss.'
Require-Contains $artifacts 'updateDonationInstanceStatus(ref.ownerUuid(), ref.uniqueItemId(), ref.itemId(), "CONSUMED"' 'Artifacts must keep CONSUMED status separate from reclaimable loss.'

Throw-IfErrors 'ValidateCopiMineDonationReclaimLossOnly'
