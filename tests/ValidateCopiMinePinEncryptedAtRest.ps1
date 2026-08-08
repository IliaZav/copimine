. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'pin_hash TEXT NOT NULL' 'PIN storage must keep a hashed column.'
Require-Contains $mainPy 'def public_pin_status(payload: Optional[dict[str, Any]]) -> dict[str, Any]:' 'PIN API responses must be projected through a secret-free status helper.'
Require-Contains $mainPy 'ALTER TABLE {table} DROP COLUMN pin_sealed' 'Startup migration must remove legacy reversible PIN columns.'
Require-NotRegex $mainPy 'INSERT INTO (bank_pin_hashes|bank_account_pins)[^\n]*pin_sealed' 'Persistent PIN writes must not include a reversible secret column.'
Require-NotRegex $mainPy 'SELECT[^\n]*pin_sealed' 'Persistent PIN reads must not load a reversible secret column.'
Require-NotRegex $mainPy 'seal_persistent_pin|reveal_persistent_pin' 'Persistent PINs must never be sealed for later recovery.'
Require-NotRegex $mainPy 'pin_plain|plain_pin' 'Backend must not persist plain PIN fields.'

Throw-IfErrors 'ValidateCopiMinePinEncryptedAtRest'
