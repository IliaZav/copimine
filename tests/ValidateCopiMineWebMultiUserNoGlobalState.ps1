. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$browserState = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'shared\browser-state.js')
$frontend = Read-FrontendBundle

Require-Contains $browserState 'const ALLOWED_KEYS = new Set([' 'Frontend storage must stay allowlisted.'
Require-Contains $browserState '"copimine.theme"' 'Theme preference may persist locally.'
Require-Contains $browserState '"copimineDonationSessionId"' 'Donation session hint may persist locally.'
Require-Contains $browserState '"copiminePlayerBankScope"' 'Selected bank scope may persist locally.'
Require-NotContains $browserState 'token' 'Frontend storage allowlist must not persist auth tokens.'
Require-NotContains $browserState 'refresh' 'Frontend storage allowlist must not persist refresh tokens.'
Require-NotRegex $frontend 'localStorage\.(setItem|getItem)\([^)]*(token|refresh|balance|player_uuid|candidate_uuid)' 'Frontend must not cache cross-user sensitive state in localStorage.'
Require-NotRegex $frontend 'sessionStorage\.(setItem|getItem)\([^)]*(token|refresh|balance|player_uuid|candidate_uuid)' 'Frontend must not cache cross-user sensitive state in sessionStorage.'

Throw-IfErrors 'ValidateCopiMineWebMultiUserNoGlobalState'
