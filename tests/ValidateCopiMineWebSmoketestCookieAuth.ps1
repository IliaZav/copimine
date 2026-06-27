. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$smoke = Read-Utf8 (Join-Path $root 'admin-web\scripts\backend_smoketest.py')

Require-Contains $smoke 'cookieAuth' 'Backend smoketest must verify cookie-based auth responses.'
Require-Contains $smoke '/api/auth/csrf' 'Backend smoketest must bootstrap CSRF before unsafe admin requests.'
Require-Contains $smoke 'appmod.CSRF_HEADER_NAME' 'Backend smoketest must use the configured CSRF header name.'
Require-NotContains $smoke 'Authorization":"Bearer "' 'Backend smoketest must not rely on bearer headers by default.'
Require-NotContains $smoke 'r.json()["token"]' 'Backend smoketest must not expect a bearer token in login response.'

Throw-IfErrors 'ValidateCopiMineWebSmoketestCookieAuth'
