. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts
$election = Read-Utf8 $Paths.Election

Require-Contains $admin 'repairProtectedBlockVisuals();' 'AdminPlus must repair ATM visuals on startup.'
Require-Contains $artifacts 'repairProtectedBlockVisuals();' 'Artifacts must repair shop visuals on startup.'
Require-Contains $election 'repairProtectedBlockVisuals();' 'ElectionCore must repair election visuals on startup.'
Require-Contains $admin 'LEFT JOIN protected_block_visuals pbv' 'AdminPlus repair must backfill ATM visuals from DB state.'
Require-Contains $artifacts 'SELECT linked_id,entity_uuid,model_id,custom_model_data FROM protected_block_visuals' 'Artifacts repair must backfill shop visuals from DB state.'
Require-Contains $election 'LEFT JOIN protected_block_visuals pbv' 'ElectionCore repair must backfill station/tax visuals from DB state.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsExistingBlocksRepaired'
