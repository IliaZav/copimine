. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/reset")' 'Backend must expose the admin player PIN reset endpoint.'
Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/randomize")' 'Backend must expose the admin player PIN randomize endpoint.'
Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/set")' 'Backend must expose the admin player PIN set endpoint.'
Require-Contains $mainPy 'must_change=0' 'Admin PIN set must persist only a verification hash and clear temporary status.'
Require-Contains $mainPy 'return reset_player_bank_pin_sync(player, actor)' 'Admin randomize must issue a temporary one-time reset, not a permanent recoverable PIN.'
Require-NotContains $mainPy '"pin": new_pin' 'Admin PIN endpoints must never return a persistent PIN value.'
Require-Contains $mainPy 'clear_bank_pin_lockout(conn, uuid)' 'Admin PIN set must clear a stale PIN lockout.'
Require-Contains $mainPy '"pinVerified": True' 'Admin PIN set must verify the stored hash before reporting success.'
Require-Contains $legacy '/api/players/${encodeURIComponent(player)}/bank-pin/reset' 'Frontend admin player screen must call the PIN reset endpoint.'

Throw-IfErrors 'ValidateCopiMineAdminCanResetPlayerPins'
