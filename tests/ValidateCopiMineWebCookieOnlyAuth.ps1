. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$web = Read-Utf8 $Paths.MainPy

Require-Contains $web 'AUTH_BEARER_FALLBACK_ENABLED = os.getenv("AUTH_BEARER_FALLBACK_ENABLED", "0").lower() in {"1", "true", "yes", "on"}' 'Web backend must keep bearer-token auth disabled by default.'
Require-Contains $web 'if request.cookies.get(AUTH_COOKIE_NAME):' 'Web backend must prefer HttpOnly cookie auth.'
Require-NotContains $web 'return {"token": token, "username": real_username, "role": "admin", "expiresIn": SESSION_TTL_SECONDS, "cookieAuth": True}' 'Admin login must not return raw bearer token JSON when cookie auth is active.'
Require-NotContains $web 'return {"token": token, "role": "player", "expiresIn": SESSION_TTL_SECONDS, "account": public_player_account(account)}' 'Player login must not return raw bearer token JSON when cookie auth is active.'
Require-NotContains $web 'return {"token": token, "role": "player", "expiresIn": SESSION_TTL_SECONDS, "account": {"id": account_id, "username": username, "minecraftName": minecraft_name, "linked": False}}' 'Player register must not return raw bearer token JSON when cookie auth is active.'
Require-Contains $web '"cookieAuth": True' 'Auth responses must explicitly report cookieAuth mode.'

Throw-IfErrors 'ValidateCopiMineWebCookieOnlyAuth'
