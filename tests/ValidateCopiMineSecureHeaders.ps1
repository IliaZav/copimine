. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'response.headers.setdefault("X-Content-Type-Options", "nosniff")' 'Backend must set X-Content-Type-Options.'
Require-Contains $mainPy 'response.headers.setdefault("X-Frame-Options", "DENY")' 'Backend must deny framing.'
Require-Contains $mainPy 'response.headers.setdefault("Referrer-Policy", "same-origin")' 'Backend must set a strict Referrer-Policy.'
Require-Contains $mainPy 'response.headers.setdefault("Permissions-Policy", "camera=(), microphone=(), geolocation=()")' 'Backend must restrict browser capabilities by default.'
Require-Contains $mainPy '"Content-Security-Policy"' 'Backend must set CSP headers.'
Require-Contains $mainPy 'Strict-Transport-Security' 'Backend must emit HSTS on HTTPS requests.'

Throw-IfErrors 'ValidateCopiMineSecureHeaders'
