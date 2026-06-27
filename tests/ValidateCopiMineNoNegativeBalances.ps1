. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy
$economy = Read-Utf8 $Paths.Economy

Require-Contains $mainPy 'balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0)' 'Web-managed balance tables must protect against negative balances at the schema level.'
Require-Contains $economy "balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0)" 'EconomyCore accounts must protect against negative balances at the schema level.'
Require-Regex $mainPy 'if int\(from_locked\["balance"\] or 0\) < amount:' 'Web transfer flow must reject transfers that would overdraw the source account.'
Require-Regex $economy 'INSUFFICIENT_(AR|DONATION_BALANCE)' 'EconomyCore must reject overdrafts before writing ledger rows.'

Throw-IfErrors 'ValidateCopiMineNoNegativeBalances'
