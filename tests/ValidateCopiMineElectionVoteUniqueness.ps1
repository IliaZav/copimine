. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$migration008 = Read-Utf8 $Paths.Migration008

# Ballot status is locked in the deposit transaction, while voter identity is
# kept in the private participation table.  The public votes row deliberately
# stores only an anonymous token, so searching it by voter_uuid is no longer a
# valid uniqueness check.
Require-Contains $election 'UPDATE ballots SET status=''DEPOSITED''' 'Vote deposit must close the issued ballot atomically.'
Require-Contains $election 'reserveVoteParticipation' 'Vote deposit must reserve one private participation slot.'
Require-Contains $election 'ON CONFLICT(election_id,round_no,voter_uuid) DO NOTHING' 'Participation reservation must be unique per voter and round.'
Require-Contains $election 'CREATE TABLE IF NOT EXISTS vote_participation' 'Migration must create the private voter participation table.'
Require-Contains $migration008 'CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_ballot_id' 'Migration must retain unique vote-by-ballot protection.'
Require-Contains $migration008 'CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_voter_round' 'Migration must retain the legacy public-row uniqueness index.'

Throw-IfErrors 'ValidateCopiMineElectionVoteUniqueness'
