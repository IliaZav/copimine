. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $legacy 'async function logout(call = true) {' 'Frontend must centralize logout state cleanup.'
Require-Contains $legacy 'removeStoredUiState("copimineDonationSessionId");' 'Logout must clear stored donation session state.'
Require-Contains $legacy 'removeStoredUiState("copiminePlayerBankScope");' 'Logout must clear stored bank scope state.'
Require-Contains $legacy 'state.role = "";' 'Logout must clear in-memory role state.'
Require-Contains $legacy 'state.user = null;' 'Logout must clear in-memory user state.'
Require-NotContains $Paths.FrontendBrowserState 'copimineLastRole' 'Stored role persistence must not remain in the allowlisted browser state keys.'

Throw-IfErrors 'ValidateCopiMineFrontendLogoutClearsState'
