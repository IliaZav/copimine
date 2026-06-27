. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/reset")' 'Backend must expose the admin player PIN reset endpoint.'
Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/randomize")' 'Backend must expose the admin player PIN randomize endpoint.'
Require-Contains $mainPy '@app.post("/api/players/{player}/bank-pin/set")' 'Backend must expose the admin player PIN set endpoint.'
Require-Contains $legacy '/api/players/${encodeURIComponent(player)}/bank-pin/reset' 'Frontend admin player screen must call the PIN reset endpoint.'

Throw-IfErrors 'ValidateCopiMineAdminCanResetPlayerPins'
