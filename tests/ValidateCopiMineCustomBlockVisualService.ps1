. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $election 'spawnOrReplaceProtectedBlockVisual(' 'ElectionCore must provide protected block visual spawn service.'
Require-Contains $election 'cleanupProtectedBlockVisuals(' 'ElectionCore must provide protected block visual cleanup service.'
Require-Contains $election 'repairProtectedBlockVisuals()' 'ElectionCore must repair protected block visuals on startup.'
Require-Contains $admin 'spawnOrReplaceProtectedBlockVisual(' 'AdminPlus must provide protected block visual spawn service for ATM.'
Require-Contains $admin 'cleanupProtectedBlockVisuals(' 'AdminPlus must provide protected block visual cleanup service for ATM.'
Require-Contains $admin 'repairProtectedBlockVisuals()' 'AdminPlus must repair ATM visuals on startup.'
Require-Contains $artifacts 'spawnOrReplaceProtectedBlockVisual(' 'CopiMineArtifacts must provide protected block visual spawn service for shops.'
Require-Contains $artifacts 'cleanupProtectedBlockVisuals(' 'CopiMineArtifacts must provide protected block visual cleanup service for shops.'
Require-Contains $artifacts 'repairProtectedBlockVisuals()' 'CopiMineArtifacts must repair shop visuals on startup.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualService'
