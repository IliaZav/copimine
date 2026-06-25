. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'CREATE TABLE IF NOT EXISTS round_candidates' 'ElectionCore schema must define round_candidates.'
Require-Contains $text 'INSERT INTO round_candidates' 'Approved candidates or second-round leaders must be written into round_candidates.'
Require-Contains $text 'DELETE FROM round_candidates WHERE election_id=? AND round_no=?' 'Second-round preparation must reset the next-round candidate list.'
Require-Contains $text 'SELECT candidate_name FROM round_candidates WHERE election_id=? AND round_no=? AND candidate_uuid=? AND active=1' 'Ballot confirmation must validate only current-round candidates.'
Require-Contains $text 'FROM round_candidates rc' 'Candidate lists and results must be loaded from round_candidates.'

Throw-IfErrors 'ValidateCopiMineElectionSecondRoundEligibility'
