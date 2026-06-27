. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$frontend = (Read-FrontendBundle) + "`n" + (Read-Utf8 $Paths.FrontendIndex)

Require-NotRegex $frontend '\bon(click|input|change|submit|keydown|keyup|load|focus|blur)\s*=' 'Frontend must not use inline DOM event handlers; use delegated listeners or module bindings instead.'

Throw-IfErrors 'ValidateCopiMineWebNoInlineDomHandlers'
