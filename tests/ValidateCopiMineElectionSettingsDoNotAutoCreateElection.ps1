. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$body = Method-Body (Read-Utf8 $Paths.Election) 'private void handleMenuAction'

Require-NotRegex $body 'manage:limit:[\s\S]*?ensureElectionExists' 'manage:limit must not auto-create an election.'
Require-NotRegex $body 'manage:term:[\s\S]*?ensureElectionExists' 'manage:term must not auto-create an election.'
Require-Contains $body 'requireActiveElectionId()' 'Settings actions must require an active election instead of auto-creating one.'

Throw-IfErrors 'ValidateCopiMineElectionSettingsDoNotAutoCreateElection'
