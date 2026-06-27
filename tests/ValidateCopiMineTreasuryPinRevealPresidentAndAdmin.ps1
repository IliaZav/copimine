. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy 'def has_treasury_access(conn: Any, account: dict[str, Any]) -> bool:' 'Treasury PIN visibility must route through the treasury-access helper.'
Require-Contains $mainPy '"visiblePin": visible_account_pin(conn, TREASURY_ACCOUNT_ID)' 'Treasury bank response must only reveal the visible PIN through the treasury account helper.'
Require-Contains $mainPy 'if not has_treasury_access(conn, account):' 'Treasury endpoints must block non-authorized players before returning PIN data.'

Throw-IfErrors 'ValidateCopiMineTreasuryPinRevealPresidentAndAdmin'
