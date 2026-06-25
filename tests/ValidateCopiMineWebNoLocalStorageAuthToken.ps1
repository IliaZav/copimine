. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$app = Read-Utf8 $Paths.FrontendApp

Require-NotContains $app 'copimineToken' 'Frontend must not persist auth bearer token in localStorage.'
Require-NotContains $app 'headers.Authorization' 'Frontend must not attach Authorization bearer header from browser state.'
Require-Contains $app 'credentials: "include"' 'Frontend API requests must use cookie-based auth.'

Throw-IfErrors 'ValidateCopiMineWebNoLocalStorageAuthToken'
