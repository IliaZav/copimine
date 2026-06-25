. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void confirmBallotChoice'

Require-Contains $body 'requireActiveElectionContext(connection)' 'confirmBallotChoice() must reload the active election context from DB.'
Require-Contains $body 'ElectionStage.VOTING' 'confirmBallotChoice() must require stage VOTING.'
Require-Contains $body 'round_candidates' 'confirmBallotChoice() must re-check candidate eligibility in round_candidates.'
Require-Contains $body 'candidateUuid.equals(player.getUniqueId().toString())' 'confirmBallotChoice() must still block self-voting.'

Throw-IfErrors 'ValidateCopiMineElectionConfirmVoteRequiresVotingStage'
