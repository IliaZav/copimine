. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$app = Read-Utf8 $Paths.FrontendApp
$index = Read-Utf8 (Join-Path (Split-Path $Paths.FrontendApp -Parent | Split-Path -Parent) 'index.html')

foreach ($token in @('onclick=', 'oninput=', 'onchange=', 'onsubmit=')) {
  Require-NotContains $app $token "Frontend app source must not use inline handler $token."
  Require-NotContains $index $token "Frontend HTML must not use inline handler $token."
}
Require-Contains $app 'data-click=' 'Frontend should use delegated data-click handlers instead of inline onclick.'
Require-Contains $app 'wireDataClickDelegation' 'Frontend must install delegated click handling.'
Require-Contains $app 'wireDataInputDelegation' 'Frontend must install delegated input handling.'

Throw-IfErrors 'ValidateCopiMineWebNoInlineOnclick'
