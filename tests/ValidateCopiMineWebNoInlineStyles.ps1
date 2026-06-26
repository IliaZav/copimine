. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$app = Read-Utf8 $Paths.FrontendApp
$index = Read-Utf8 (Join-Path (Split-Path $Paths.FrontendApp -Parent | Split-Path -Parent) 'index.html')

Require-NotContains $app 'style=' 'Frontend app source must not emit inline style attributes under strict CSP.'
Require-NotContains $index 'style=' 'Frontend HTML must not use inline style attributes under strict CSP.'

Throw-IfErrors 'ValidateCopiMineWebNoInlineStyles'
