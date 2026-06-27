. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts

Require-NotRegex $mainPy 'audit_event\([^)]*(pin|PIN)[^)]*visiblePin' 'Admin-web audit must not log visible PIN values.'
Require-NotRegex $mainPy 'append_panel_event\([^)]*(pin|PIN)[^)]*visiblePin' 'Panel events must not include raw PIN values.'
Require-NotRegex $economy 'getLogger\(\)\.(info|warning|severe)\([^;\r\n]*(\+\s*(pin|oldPin|newPin|enteredPin)\b|\b(pin|oldPin|newPin|enteredPin)\s*\+)' 'EconomyCore must not concatenate raw PIN values into logs.'
Require-NotRegex $artifacts 'getLogger\(\)\.(info|warning|severe)\([^;\r\n]*(\+\s*(pin|oldPin|newPin|enteredPin)\b|\b(pin|oldPin|newPin|enteredPin)\s*\+)' 'Artifacts must not log raw PIN values.'

Throw-IfErrors 'ValidateCopiMinePinNotLogged'
