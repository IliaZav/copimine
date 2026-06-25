. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election

Require-Contains $election '"candidate_" + electionId + "_" + string(' 'Candidate id must include election id.'
Require-Contains $election "CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player ON candidates(election_id,player_uuid)" 'Candidates must stay unique per election and player.'

Throw-IfErrors 'ValidateCopiMineElectionCandidateIdIncludesElection'
