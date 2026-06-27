. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin

Require-Contains $admin 'private CopiMineEconomyCore economyCore()' 'AdminPlus must resolve EconomyCore through a bridge method.'
Require-Contains $admin 'economy.openAdminEconomyHub(p)' 'AdminPlus economy hub action must delegate to EconomyCore.'
Require-Contains $admin 'if(a.equals("open:bank-atms")||a.equals("bank-atm:create-target")||a.startsWith("bank-atm:delete:")||a.startsWith("open:bank-atm:"))' 'AdminPlus must trap legacy ATM admin actions explicitly.'
Require-NotContains $admin 'openEconomy(p);' 'AdminPlus must not open the legacy local economy hub from active actions.'
Require-NotContains $admin 'economy.openAtmDirectory(p)' 'AdminPlus must not own an ATM directory flow anymore.'
Require-NotContains $admin 'economy.atmService().openAtm' 'AdminPlus must not proxy live ATM click handling anymore.'
Require-NotContains $admin 'economyCore().isAtmBlock' 'AdminPlus must not inspect ATM blocks directly in active runtime.'
Require-NotContains $admin 'private void openEconomy(Player p)' 'AdminPlus must not keep a local economy root implementation.'
Require-NotContains $admin 'private void openBankAtms(Player p)' 'AdminPlus must not keep a local ATM registry GUI.'
Require-NotContains $admin 'private void openBankAtm(Player p,String atmId)' 'AdminPlus must not keep a local ATM player GUI.'
Require-NotContains $admin 'private boolean isBankAtmBlock(Block b)' 'AdminPlus must not own ATM block detection.'
Require-NotContains $admin 'private String createBankAtmFromTarget(Player p)' 'AdminPlus must not own ATM creation flow.'
Require-NotContains $admin 'private String archiveBankAtm(Player p,String id)' 'AdminPlus must not own ATM archive flow.'
Require-NotContains $admin 'private int syncArOnline(String actor)' 'AdminPlus must not own AR balance synchronization.'
Require-NotContains $admin 'private void syncAr(Player p)' 'AdminPlus must not own direct AR sync logic.'

Throw-IfErrors 'ValidateCopiMineAdminPlusNoEconomyOwnership'
