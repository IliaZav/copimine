. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$migration = Read-Utf8 $Paths.Migration008

Require-Contains $text "status IN ('ISSUED','CONFIRMED','DEPOSITED')" 'Active ballot checks must include DEPOSITED ballots.'
Require-Contains $migration "status IN ('ISSUED', 'CONFIRMED', 'DEPOSITED')" 'Active-ballot unique index migration must include DEPOSITED ballots.'

Throw-IfErrors 'ValidateCopiMineElectionDepositedBallotPreventsNewBallot'
