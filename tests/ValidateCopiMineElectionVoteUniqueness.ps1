. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$migration008 = Read-Utf8 $Paths.Migration008

Require-Contains $election 'SELECT COUNT(*) FROM votes WHERE ballot_id=?' 'Vote deposit must reject duplicate ballot submission.'
Require-Contains $election 'SELECT COUNT(*) FROM votes WHERE election_id=? AND round_no=? AND voter_uuid=?' 'Vote deposit must reject a second accepted vote from the same voter in a round.'
Require-Contains $migration008 'CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_ballot_id' 'Migration must add unique vote-by-ballot protection.'
Require-Contains $migration008 'CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_voter_round' 'Migration must add unique vote-by-voter-per-round protection.'

Throw-IfErrors 'ValidateCopiMineElectionVoteUniqueness'
