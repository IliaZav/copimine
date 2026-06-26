. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy "script-src 'self'" 'CSP must allow only self-hosted scripts.'
Require-NotContains $mainPy "script-src 'self' 'unsafe-inline'" 'CSP must not allow unsafe-inline scripts.'
Require-Contains $mainPy "style-src 'self'" 'CSP must allow only self-hosted styles.'
Require-NotContains $mainPy "style-src 'self' 'unsafe-inline'" 'CSP must not allow unsafe-inline styles.'
Require-Contains $mainPy "object-src 'none'" 'CSP must disable object/embed execution.'

Throw-IfErrors 'ValidateCopiMineWebNoUnsafeInlineCsp'
