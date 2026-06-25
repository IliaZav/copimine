. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin

Require-Contains $admin 'private CopiMineEconomyCore economyCore()' 'AdminPlus must resolve EconomyCore through a bridge method.'
Require-Contains $admin 'economy.openAdminEconomyHub(p)' 'AdminPlus economy hub action must delegate to EconomyCore.'
Require-Contains $admin 'economy.openAtmDirectory(p)' 'AdminPlus ATM directory action must delegate to EconomyCore.'
Require-Contains $admin 'economy.atmService().openAtm' 'AdminPlus ATM interaction must delegate to EconomyCore.'
Require-NotContains $admin 'openEconomy(p);' 'AdminPlus must not open the legacy local economy hub from active actions.'

Throw-IfErrors 'ValidateCopiMineAdminPlusNoEconomyOwnership'
