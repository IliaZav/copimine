. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $election 'repairProtectedBlockVisuals();' 'Startup must trigger protected block visual repair.'
Require-Contains $election 'public void onChunkLoad(ChunkLoadEvent event)' 'Chunk load repair hook must exist for protected block visuals.'
Require-Contains $admin 'repairProtectedBlockVisuals();' 'AdminPlus startup must trigger ATM visual repair.'
Require-Contains $admin 'public void onChunkLoad(ChunkLoadEvent e)' 'AdminPlus chunk load repair hook must exist for ATM visuals.'
Require-Contains $artifacts 'repairProtectedBlockVisuals();' 'CopiMineArtifacts startup must trigger shop visual repair.'
Require-Contains $artifacts 'public void onChunkLoad(ChunkLoadEvent event)' 'CopiMineArtifacts chunk load repair hook must exist for shop visuals.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsRepairOnRestart'
