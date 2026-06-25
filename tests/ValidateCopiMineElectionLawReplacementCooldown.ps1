. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$submit = Method-Body $text 'private void submitLawForReview'
$review = Method-Body $text 'private void reviewLaw'

Require-NotContains $submit 'UPDATE president_terms SET last_law_replace_at=?' 'Law replacement cooldown must not start before approval.'
Require-Contains $review 'UPDATE president_terms SET last_law_replace_at=?' 'Law replacement cooldown must start on approve.'

Throw-IfErrors 'ValidateCopiMineElectionLawReplacementCooldown'
