. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts "UPDATE artifact_item_instances SET status='DELETED_AS_INVALID',updated_at=? WHERE unique_item_id=? AND status='DELIVERING'" 'Donation failure cleanup must invalidate only DELIVERING instances.'
Require-Contains $artifacts 'Donation instance failure cleanup lost DELIVERING state.' 'Donation failure cleanup must fail closed when the instance status changed unexpectedly.'

Throw-IfErrors 'ValidateCopiMineArtifactsDonationFailedCleanupStatusGuard'
