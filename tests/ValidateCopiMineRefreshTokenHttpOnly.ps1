. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'def set_refresh_cookie(response: Response, token: str, max_age: int = REFRESH_TOKEN_TTL_SECONDS) -> None:' 'Backend must centralize refresh cookie writing in a dedicated helper.'
Require-Contains $mainPy 'httponly=True' 'Refresh cookies must stay HttpOnly.'
Require-Contains $mainPy 'set_refresh_cookie(response, refresh_token)' 'Auth responses must set refresh tokens through the cookie helper.'

Throw-IfErrors 'ValidateCopiMineRefreshTokenHttpOnly'
