. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-NotContains $text '"cmnarcotics"' 'CopiMineElectionCore must not block /cmnarcotics.'

Throw-IfErrors 'ValidateCopiMineElectionCoreDoesNotBlockNarcotics'
