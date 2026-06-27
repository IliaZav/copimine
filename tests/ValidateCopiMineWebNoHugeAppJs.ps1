. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$app = Read-Utf8 $Paths.FrontendApp
$lineCount = ($app -split "`r?`n").Count

Require-Contains $app 'import "./js/bootstrap.js";' 'app.js must remain a bootstrap-only entrypoint.'
Require-NotContains $app 'fetch(' 'app.js must not own API calls directly.'
Require-NotContains $app 'innerHTML' 'app.js must not render page markup directly.'
if ($lineCount -gt 8) {
  $errors.Add("app.js grew to $lineCount lines and is no longer a thin entrypoint.")
}

Throw-IfErrors 'ValidateCopiMineWebNoHugeAppJs'
