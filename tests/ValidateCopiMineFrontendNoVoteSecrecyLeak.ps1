. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$frontend = Read-Utf8 $Paths.FrontendApp

Require-NotContains $frontend 'voter_uuid' 'Frontend must not render voter UUIDs.'
Require-NotContains $frontend 'voter_name' 'Frontend must not render voter names.'
Require-NotContains $frontend 'ballot_id' 'Frontend must not render ballot ids in election UI.'
Require-NotContains $frontend 'taxPayments' 'Frontend election UI must not rely on public tax payment rows.'
Require-NotContains $frontend 'candidate_name' 'Frontend election UI must use sanitized candidate fields only.'

Throw-IfErrors 'ValidateCopiMineFrontendNoVoteSecrecyLeak'
