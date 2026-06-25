. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

$issueApp = Method-Body $text 'private void issueApplicationBook(Player target, Player issuer) throws Exception {'
$issueBallot = Method-Body $text 'private void issueBallot(Player target, Player issuer) throws Exception {'
if (-not $issueApp) { $errors.Add('issueApplicationBook() not found.') }
if (-not $issueBallot) { $errors.Add('issueBallot() not found.') }
if ($issueApp) {
  Require-Contains $issueApp 'requireActiveElectionId()' 'issueApplicationBook() must require an active election.'
  Require-NotContains $issueApp 'ensureElectionExists(' 'issueApplicationBook() must not auto-create elections.'
}
if ($issueBallot) {
  Require-Contains $issueBallot 'requireActiveElectionId()' 'issueBallot() must require an active election.'
  Require-NotContains $issueBallot 'ensureElectionExists(' 'issueBallot() must not auto-create elections.'
}

Throw-IfErrors 'ValidateCopiMineElectionNoAutoCreateByCik'
