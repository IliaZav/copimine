. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'private SealContext validateSealUsage' 'ElectionCore must validate seals through DB-backed SealContext.'
Require-Contains $text "WHERE s.id=?" 'Seal validation must resolve the exact seal row.'
Require-Contains $text "status='ACTIVE'" 'Seal validation must enforce ACTIVE status.'
Require-Contains $text 'station_active' 'Seal validation must require an active station.'
Require-Contains $text 'election_active' 'Seal validation must require an active election.'
Require-Contains $text 'chair_active' 'Seal validation must require an active chair assignment.'
Require-Contains $text 'removeOfficialItemsFromPlayer(player, "CIK_SEAL")' 'Revoked or invalid seals must be removable from players.'

Throw-IfErrors 'ValidateCopiMineElectionSealSecurity'
