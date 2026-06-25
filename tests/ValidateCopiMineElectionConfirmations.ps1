. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'private void openConfirmationMenu' 'Confirmation GUI helper is missing.'
Require-Contains $text '"confirm:apply"' 'Confirmation GUI must expose apply action.'
Require-Contains $text '"confirm:back"' 'Confirmation GUI must expose back action.'
foreach ($needle in @(
  'action.equals("manage:stop")',
  'action.startsWith("stage:")',
  'action.startsWith("station:remove-protection:")',
  'action.equals("cik:revoke-all")',
  'action.startsWith("application:approve:")',
  'action.startsWith("application:reject:")',
  'action.equals("results:count")',
  'action.equals("results:second-round")',
  'action.startsWith("results:winner:")',
  'action.equals("president:remove")',
  'action.startsWith("law:approve:")',
  'action.startsWith("law:reject:")',
  'action.startsWith("tax:set:")',
  'action.startsWith("chair:annul-ballot:")',
  'action.startsWith("vote:confirm:")',
  'action.startsWith("mandate:tax:")'
)) {
  Require-Contains $text $needle "Missing confirmation-routed action: $needle"
}

Throw-IfErrors 'ValidateCopiMineElectionConfirmations'
