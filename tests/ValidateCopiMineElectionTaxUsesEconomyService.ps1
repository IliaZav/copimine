. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$pluginYml = Read-Utf8 $Paths.ElectionPluginYml

$payBank = Method-Body $election 'private void payTaxFromBank(Player player, String taxId, String pin) throws Exception {'
$payInventory = Method-Body $election 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

if ($null -eq $payBank) { $errors.Add('payTaxFromBank method not found.') }
if ($null -eq $payInventory) { $errors.Add('payTaxWithInventory method not found.') }
if ($payBank) {
  Require-Contains $payBank 'requireEconomyBankService()' 'Tax payment from bank must resolve EconomyCore BankService.'
  Require-Contains $payBank '.transferWithPin(' 'Tax payment from bank must transfer through EconomyCore BankService.'
  Require-NotContains $payBank 'cmv4_bank_' 'Tax payment from bank must not write bank tables directly.'
}
if ($payInventory) {
  Require-Contains $payInventory 'requireEconomyBankService()' 'Tax payment from inventory must resolve EconomyCore BankService.'
  Require-Contains $payInventory '.credit(' 'Tax payment from inventory must credit through EconomyCore BankService.'
  Require-NotContains $payInventory 'cmv4_bank_' 'Tax payment from inventory must not write bank tables directly.'
}
Require-Contains $pluginYml 'CopiMineEconomyCore' 'ElectionCore plugin.yml must softdepend on EconomyCore.'

Throw-IfErrors 'ValidateCopiMineElectionTaxUsesEconomyService'
