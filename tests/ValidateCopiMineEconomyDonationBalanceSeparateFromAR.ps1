. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy

Require-Contains $economy 'interface DonationBalanceService' 'EconomyCore must declare DonationBalanceService.'
Require-Contains $economy 'CREATE TABLE IF NOT EXISTS donation_accounts' 'EconomyCore must create donation_accounts table.'
Require-Contains $economy 'CREATE TABLE IF NOT EXISTS donation_balance_ledger' 'EconomyCore must create donation balance ledger.'
Require-NotContains $economy 'currency=''DONATION''' 'Donation balance must not be merged into AR bank accounts.'

Throw-IfErrors 'ValidateCopiMineEconomyDonationBalanceSeparateFromAR'
