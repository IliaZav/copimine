. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$frontend = Read-FrontendBundle
$mainPy = Read-Utf8 $Paths.MainPy

Require-NotRegex $frontend 'location\.(href|assign|replace)\s*=\s*[^;]*(next|redirect|returnUrl|targetUrl)' 'Frontend must not perform open redirects from user-controlled next/redirect parameters.'
Require-NotRegex $frontend 'window\.open\([^)]*(next|redirect|returnUrl|targetUrl)' 'Frontend must not open unvalidated redirect targets.'
Require-NotRegex $mainPy 'RedirectResponse\([^)]*(next|redirect|returnUrl|targetUrl)' 'Backend must not expose raw redirect parameters through RedirectResponse.'

Throw-IfErrors 'ValidateCopiMineNoOpenRedirect'
