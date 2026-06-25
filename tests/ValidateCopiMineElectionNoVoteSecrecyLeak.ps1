. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$mainPy = Read-Utf8 $Paths.MainPy
$detail = Method-Body $mainPy 'def election_detail_sync(limit: int = 500) -> dict[str, Any]:'

Require-NotContains $election 'payload.put("votes"' 'web-data must not expose raw votes.'
Require-NotContains $election 'SELECT ballot_id,voter_name,candidate_name,station_id,created_at FROM votes' 'web-data must not expose voter -> candidate linkage.'
Require-NotRegex $detail '"votes"\s*:\s*votes' 'Admin web API must not return raw vote rows.'
Require-NotRegex $detail 'voter_(uuid|name)' 'Admin web election detail must not expose voter identity.'
Require-NotContains $detail '"taxPayments"' 'Admin web election detail must not expose public tax payment rows.'

Throw-IfErrors 'ValidateCopiMineElectionNoVoteSecrecyLeak'
