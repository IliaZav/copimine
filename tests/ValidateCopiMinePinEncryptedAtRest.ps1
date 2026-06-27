. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'pin_hash TEXT NOT NULL' 'PIN storage must keep a hashed column.'
Require-Contains $mainPy 'pin_sealed TEXT NOT NULL DEFAULT ' "''" 'PIN storage must keep a sealed recovery column.'
Require-Contains $mainPy 'def seal_persistent_pin(pin_key: str, pin: str) -> str:' 'Backend must seal persistent PIN values at rest.'
Require-Contains $mainPy 'def reveal_persistent_pin(pin_key: str, sealed: str) -> str:' 'Backend must reveal sealed PIN values through a dedicated helper only.'
Require-NotRegex $mainPy 'pin_plain|plain_pin' 'Backend must not persist plain PIN fields.'

Throw-IfErrors 'ValidateCopiMinePinEncryptedAtRest'
