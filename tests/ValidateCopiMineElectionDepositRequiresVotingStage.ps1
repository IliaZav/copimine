. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void depositBallot'

Require-Contains $body 'requireActiveElectionContext(connection)' 'depositBallot() must reload the active election context from DB.'
Require-Contains $body 'ElectionStage.VOTING' 'depositBallot() must require stage VOTING.'
Require-Contains $body 'currentRoundFromDb(connection' 'depositBallot() must compare against the live current round.'
Require-Contains $body 'stationMatchesOrFallback' 'depositBallot() must still enforce station ownership or fallback rules.'

Throw-IfErrors 'ValidateCopiMineElectionDepositRequiresVotingStage'
