. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$app = Read-Utf8 $Paths.FrontendApp

Require-Contains $mainPy 'CSRF_COOKIE_NAME' 'Backend must define CSRF cookie name.'
Require-Contains $mainPy 'CSRF_HEADER_NAME' 'Backend must define CSRF header name.'
Require-Contains $mainPy 'verify_csrf_token' 'Backend must verify CSRF tokens.'
Require-Contains $mainPy 'csrf_exempt_paths' 'Backend must scope CSRF exemptions explicitly.'
Require-Contains $mainPy '@app.get("/api/auth/csrf")' 'Backend must expose CSRF bootstrap endpoint.'
Require-Contains $app 'const CSRF_HEADER = "X-CSRF-Token";' 'Frontend must know the CSRF header name.'
Require-Contains $app 'refreshCsrfCookie' 'Frontend must refresh CSRF cookie.'
Require-Contains $app 'headers[CSRF_HEADER] = csrf;' 'Frontend must attach CSRF header to unsafe requests.'

Throw-IfErrors 'ValidateCopiMineWebCsrfProtection'
