. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'Material.WRITTEN_BOOK' 'Application submission must require WRITTEN_BOOK.'
Require-Contains $text 'readString(item, applicationIdKey)' 'Application submission must validate application_id.'
Require-Contains $text 'readString(item, electionIdKey)' 'Application submission must validate election_id.'
Require-Contains $text 'readString(item, stationIdKey)' 'Application submission must validate station_id.'
Require-Contains $text 'readString(item, playerUuidKey)' 'Application submission must validate player_uuid.'
Require-Contains $text "WHERE id=? AND status='ISSUED' AND submitted_at=0" 'Application submission must be idempotent and reject resubmits.'

Throw-IfErrors 'ValidateCopiMineElectionApplicationBookSigned'
