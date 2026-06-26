. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $mainPy 'AUTH_REFRESH_COOKIE_NAME' 'Backend must define a dedicated refresh cookie name.'
Require-Contains $mainPy 'cm_refresh_sessions' 'Backend must persist refresh-session state for rotation and revoke.'
Require-Contains $mainPy 'def make_refresh_token(' 'Backend must mint refresh tokens separately from access tokens.'
Require-Contains $mainPy 'def verify_refresh_token(' 'Backend must verify refresh tokens against persisted session state.'
Require-Contains $mainPy 'def revoke_refresh_session(' 'Backend must revoke used refresh tokens during rotation.'
Require-Contains $mainPy '@app.post("/api/auth/refresh")' 'Admin auth must expose refresh endpoint.'
Require-Contains $mainPy '@app.post("/api/player/refresh")' 'Player auth must expose refresh endpoint.'
Require-Contains $mainPy 'revoke_refresh_session(str(payload.get("jti") or ""), str(refresh_payload.get("jti") or ""))' 'Refresh flow must revoke the old token after rotation.'
Require-Contains $mainPy 'httponly=True' 'Refresh cookie must stay HttpOnly.'
Require-NotRegex $legacy 'localStorage\.(setItem|getItem)\([^)]*(refresh|token)' 'Frontend must not store refresh/access tokens in localStorage while using refresh rotation.'
Require-Contains $legacy 'tryRefreshSession' 'Frontend must attempt silent refresh when cookie session expires.'

Throw-IfErrors 'ValidateCopiMineRefreshTokenRotation'
