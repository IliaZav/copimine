. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $economy 'CREATE UNIQUE INDEX IF NOT EXISTS ux_donation_balance_ledger_idempotency' 'Donation ledger must protect idempotency keys.'
Require-Contains $economy 'INSERT INTO donation_balance_ledger' 'EconomyCore must write donation ledger entries.'
Require-Contains $mainPy 'donation_balance_ledger' 'admin-web must know about donation ledger table.'

Throw-IfErrors 'ValidateCopiMineEconomyDonationLedger'
