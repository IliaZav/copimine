. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Admin

Require-Contains $text 'private boolean isLegacyElectionAction(String action)' 'AdminPlus must centralize legacy election action guards.'
Require-Contains $text '|| action.startsWith("chair:")' 'Chair legacy actions must be blocked.'
Require-Contains $text '|| action.startsWith("app-review:")' 'Application review legacy actions must be blocked.'
Require-Contains $text '|| action.equals("open:election-integrity")' 'Old election integrity GUI must be blocked from execution.'
Require-Contains $text 'if(isLegacyElectionAction(a)){redirectLegacyElectionAction(p); return;}' 'Legacy election actions must redirect before execution.'

Throw-IfErrors 'ValidateCopiMineNoLegacyElectionActionExecution'
