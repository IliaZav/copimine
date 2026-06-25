. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election
$body = Method-Body $text 'private void handleMenuAction'
if (-not $body) {
  $errors.Add('handleMenuAction() was not found.')
} else {
  foreach ($pattern in @(
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
    $count = ([regex]::Matches($body, [regex]::Escape($pattern))).Count
    if ($count -ne 1) {
      $errors.Add("Action route must appear exactly once in handleMenuAction(): $pattern (found $count)")
    }
  }
}

Throw-IfErrors 'ValidateCopiMineElectionNoDuplicateActionHandlers'
