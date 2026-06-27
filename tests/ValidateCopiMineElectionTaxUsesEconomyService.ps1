. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$election = Read-Utf8 $Paths.Election
$pluginYml = Read-Utf8 $Paths.ElectionPluginYml

$payBank = Method-Body $election 'private void payTaxFromBank(Player player, String taxId, String pin) throws Exception {'
$payInventory = Method-Body $election 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

if ($null -eq $payBank) { $errors.Add('payTaxFromBank method not found.') }
if ($null -eq $payInventory) { $errors.Add('payTaxWithInventory method not found.') }
if ($payBank) {
  Require-Contains $payBank '\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.' 'Tax payment from bank must be hard-disabled after removal of the mechanic.'
  Require-NotContains $payBank 'cmv4_bank_' 'Disabled tax payment from bank must not write bank tables directly.'
}
if ($payInventory) {
  Require-Contains $payInventory '\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442\u0441\u043a\u0438\u0439 \u043d\u0430\u043b\u043e\u0433 \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d.' 'Tax payment from inventory must be hard-disabled after removal of the mechanic.'
  Require-NotContains $payInventory 'cmv4_bank_' 'Disabled tax payment from inventory must not write bank tables directly.'
}
Require-Contains $pluginYml 'CopiMineEconomyCore' 'ElectionCore plugin.yml must softdepend on EconomyCore.'

Throw-IfErrors 'ValidateCopiMineElectionTaxUsesEconomyService'
