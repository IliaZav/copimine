. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'def has_treasury_access(conn: Any, account: dict[str, Any]) -> bool:' 'Treasury PIN visibility must route through the treasury-access helper.'
Require-NotContains $mainPy 'visiblePin' 'Treasury PIN must never be returned, even to an authorized viewer.'
Require-NotContains $mainPy 'visible_account_pin' 'Treasury views must use hash verification rather than PIN recovery.'
Require-Contains $mainPy 'if not has_treasury_access(conn, account):' 'Treasury endpoints must block non-authorized players before returning PIN data.'

Throw-IfErrors 'ValidateCopiMineTreasuryPinRevealPresidentAndAdmin'
