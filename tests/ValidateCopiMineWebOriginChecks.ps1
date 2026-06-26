. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'request.headers.get("origin")' 'Security middleware must inspect Origin for state-changing requests.'
Require-Contains $mainPy 'origin_allowed(request, origin)' 'Security middleware must validate Origin against allowed values.'
Require-Contains $mainPy 'request.headers.get("sec-fetch-site")' 'Security middleware must inspect Sec-Fetch-Site for state-changing requests.'
Require-Contains $mainPy 'not in {"same-origin", "same-site", "none"}' 'Backend must reject unexpected Sec-Fetch-Site values.'
Require-Contains $mainPy 'cookie_token != header_token' 'Backend must compare CSRF cookie and CSRF header.'
Require-Contains $mainPy 'verify_csrf_token(cookie_token)' 'Backend must validate signed CSRF cookie values.'

Throw-IfErrors 'ValidateCopiMineWebOriginChecks'
