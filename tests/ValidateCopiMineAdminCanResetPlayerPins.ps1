. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/reset")' 'Backend must expose the admin player PIN reset endpoint.'
Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/randomize")' 'Backend must expose the admin player PIN randomize endpoint.'
Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/set")' 'Backend must expose the admin player PIN set endpoint.'
Require-Contains $mainPy 'pin_sealed=excluded.pin_sealed' 'Admin PIN set must update the sealed PIN used for display and diagnostics.'
Require-Contains $mainPy 'clear_bank_pin_lockout(conn, uuid)' 'Admin PIN set must clear a stale PIN lockout.'
Require-Contains $mainPy '"pinVerified": True' 'Admin PIN set must verify the stored hash before reporting success.'
Require-Contains $legacy '/api/players/${encodeURIComponent(player)}/bank-pin/reset' 'Frontend admin player screen must call the PIN reset endpoint.'

Throw-IfErrors 'ValidateCopiMineAdminCanResetPlayerPins'
