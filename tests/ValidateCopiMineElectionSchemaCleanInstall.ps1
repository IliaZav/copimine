. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$migration007 = Read-Utf8 $Paths.Migration007
$migration008 = Read-Utf8 $Paths.Migration008

$createPos = $election.IndexOf('CREATE TABLE IF NOT EXISTS elections')
$alterPos = $election.IndexOf('ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_stage')
if ($createPos -lt 0 -or $alterPos -lt 0 -or $createPos -gt $alterPos) {
  $errors.Add('ensureSchema() must create elections before altering it.')
}
Require-Contains $migration007 'CREATE TABLE IF NOT EXISTS elections' 'Base election rebuild migration must create elections.'
Require-Contains $migration008 'CREATE TABLE IF NOT EXISTS round_candidates' 'Phase 1 migration must add round_candidates.'
Require-Contains $migration008 'ALTER TABLE elections ADD COLUMN IF NOT EXISTS created_at' 'Phase 1 migration must backfill elections.created_at.'
Require-Contains $migration008 'ALTER TABLE elections ADD COLUMN IF NOT EXISTS updated_at' 'Phase 1 migration must backfill elections.updated_at.'

Throw-IfErrors 'ValidateCopiMineElectionSchemaCleanInstall'
