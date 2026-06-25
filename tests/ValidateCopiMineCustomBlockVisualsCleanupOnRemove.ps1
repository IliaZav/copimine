. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $election 'cleanupProtectedBlockVisuals("POLLING_STATION", stationId);' 'Station removal must clean protected block visuals.'
Require-Contains $election "DELETE FROM protected_block_visuals WHERE kind IN ('POLLING_STATION','TAX_OFFICE')" 'Election reset must delete protected block visual rows.'
Require-Contains $admin 'cleanupProtectedBlockVisuals("ATM",id);' 'ATM archive must clean protected block visuals.'
Require-Contains $artifacts 'cleanupProtectedBlockVisuals("ARTIFACT_SHOP", shop.shopId());' 'Artifact shop removal must clean protected block visuals.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsCleanupOnRemove'
