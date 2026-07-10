. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$pluginYml = Read-Utf8 $Paths.ElectionPluginYml

$payBank = Method-Body $election 'private void payTaxFromBank(Player player, String taxId, String pin) throws Exception {'
$payInventory = Method-Body $election 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

if ($null -eq $payBank) { $errors.Add('payTaxFromBank method not found.') }
if ($null -eq $payInventory) { $errors.Add('payTaxWithInventory method not found.') }
if ($payBank) {
  Require-Contains $payBank 'requireEconomyBankService().transferToAccount(' 'Tax payment from bank must go through EconomyCore bank service.'
  Require-Contains $payBank 'dueTaxAmount(' 'Bank tax payment flow must calculate only the current due amount.'
  Require-NotContains $payBank 'cmv4_bank_' 'Disabled tax payment from bank must not write bank tables directly.'
}
if ($payInventory) {
  Require-Contains $payInventory 'throw new IllegalStateException(' 'Inventory tax entry point must stay disabled in favor of AR bank payments.'
  Require-NotContains $payInventory 'cmv4_bank_' 'Inventory tax entry point must not write bank tables directly.'
}
Require-Contains $pluginYml 'CopiMineEconomyCore' 'ElectionCore plugin.yml must softdepend on EconomyCore.'

Throw-IfErrors 'ValidateCopiMineElectionTaxUsesEconomyService'
