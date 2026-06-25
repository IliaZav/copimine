. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $election 'CREATE TABLE IF NOT EXISTS protected_block_visuals' 'Schema must create protected_block_visuals table.'
Require-Contains $election 'INSERT INTO protected_block_visuals(' 'Visual entities must be persisted in PostgreSQL.'
Require-Contains $election 'idx_protected_block_visuals_linked' 'Visual linked-id index must exist.'
Require-Contains $admin 'CREATE TABLE IF NOT EXISTS protected_block_visuals' 'AdminPlus must create protected_block_visuals table.'
Require-Contains $admin 'INSERT INTO protected_block_visuals(' 'AdminPlus must persist ATM visual rows in PostgreSQL.'
Require-Contains $artifacts 'CREATE TABLE IF NOT EXISTS protected_block_visuals' 'CopiMineArtifacts must create protected_block_visuals table.'
Require-Contains $artifacts 'INSERT INTO protected_block_visuals(' 'CopiMineArtifacts must persist shop visual rows in PostgreSQL.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsPersistInDb'
