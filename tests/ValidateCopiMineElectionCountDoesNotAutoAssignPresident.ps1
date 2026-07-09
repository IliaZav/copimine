. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void countCurrentRoundStrict'

Require-NotContains $body 'assignPresident(' 'countCurrentRound() must not auto-assign the president.'
Require-Contains $body 'manual_winner_uuid' 'countCurrentRound() must persist a pending winner for manual confirmation.'
Require-Contains $body 'manual_winner_name' 'countCurrentRound() must persist a pending winner name for manual confirmation.'

Throw-IfErrors 'ValidateCopiMineElectionCountDoesNotAutoAssignPresident'
