. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$detail = Method-Body $mainPy 'def election_detail_sync(limit: int = 500) -> dict[str, Any]:'

Require-NotContains $detail '"taxPayments"' 'Election detail API must not expose taxPayments.'
Require-NotContains $detail '"votes": votes' 'Election detail API must not expose raw vote rows.'
Require-NotRegex $detail 'voter_(uuid|name)' 'Election detail API must not expose voter identity.'

Throw-IfErrors 'ValidateCopiMineWebNoVoteSecrecyLeak'
