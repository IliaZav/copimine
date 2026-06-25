. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$pending = Method-Body $text 'private List<Map<String, Object>> pendingLaws'
$published = Method-Body $text 'private List<Map<String, Object>> publishedLaws'

Require-Contains $pending 'activeTerm()' 'pendingLaws() must scope results to the active president term.'
Require-Contains $pending 'term_id=?' 'pendingLaws() must filter by term_id.'
Require-Contains $published 'activeTerm()' 'publishedLaws() must scope results to the active president term.'
Require-Contains $published 'term_id=?' 'publishedLaws() must filter by term_id.'

Throw-IfErrors 'ValidateCopiMineElectionLawsScopedToActiveTerm'
