. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$legacy = Read-Utf8 $Paths.FrontendLegacy

Require-Contains $legacy 'async function dangerConfirm(' 'Legacy frontend must expose async confirm flow.'
Require-Contains $legacy 'window.modalConfirmAccept' 'Danger confirm must resolve through modal buttons instead of typed prompt.'
Require-Contains $legacy 'window.modalConfirmCancel' 'Danger confirm must support explicit cancel action.'
Require-NotContains $legacy 'const typed = prompt(' 'Danger confirm must not rely on typed prompt confirmation.'
Require-Regex $legacy 'await dangerConfirm\(' 'Dangerous actions must await the modal confirmation before mutating state.'

Throw-IfErrors 'ValidateCopiMineWebDangerConfirmModal'
