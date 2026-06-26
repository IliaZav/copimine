. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$app = Read-Utf8 $Paths.FrontendApp
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-NotContains $app 'copimineToken' 'Frontend must not persist auth bearer token in localStorage.'
Require-NotContains $app 'headers.Authorization' 'Frontend must not attach Authorization bearer header from browser state.'
Require-Contains $app 'bootstrap.js' 'Frontend bootstrap entry must keep delegating to the modular runtime.'
Require-NotContains $legacy 'copimineToken' 'Legacy frontend must not persist auth bearer token in localStorage.'
Require-NotContains $legacy 'headers.Authorization' 'Legacy frontend must not attach Authorization bearer header from browser state.'
Require-NotContains $legacy 'AUTH_REFRESH_COOKIE_NAME' 'Legacy frontend must not know refresh-cookie internals.'
Require-NotRegex $legacy 'localStorage\.(setItem|getItem)\([^)]*(refresh|token)' 'Legacy frontend must not persist refresh or access tokens in browser storage.'
Require-Contains $legacy 'credentials: "include"' 'Legacy frontend API requests must use cookie-based auth.'

Throw-IfErrors 'ValidateCopiMineWebNoLocalStorageAuthToken'
