. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$ballotsBody = Method-Body $text 'private void openChairBallotsMenu'
$annulBody = Method-Body $text 'private void annulBallot'

Require-Contains $annulBody "FOR UPDATE" 'annulBallot() must lock the ballot row before changing status.'
Require-Contains $annulBody "DEPOSITED" 'annulBallot() must explicitly guard deposited ballots.'
Require-Contains $annulBody 'throw new IllegalStateException(' 'annulBallot() must explain why deposited ballots cannot be annulled.'
Require-NotContains $annulBody "DELETE FROM votes WHERE ballot_id=?" 'annulBallot() must not blindly delete deposited votes.'
Require-Contains $ballotsBody '"ISSUED"' 'Chair ballot list must differentiate ISSUED ballots.'
Require-Contains $ballotsBody '"CONFIRMED"' 'Chair ballot list must differentiate CONFIRMED ballots.'
Require-Contains $ballotsBody '"DEPOSITED"' 'Chair ballot list must differentiate DEPOSITED ballots.'

Throw-IfErrors 'ValidateCopiMineElectionCannotAnnulDepositedBallot'
