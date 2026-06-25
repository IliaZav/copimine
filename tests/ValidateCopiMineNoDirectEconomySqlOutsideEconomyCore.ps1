. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$election = Read-Utf8 $Paths.Election
$payBank = Method-Body $election 'private void payTaxFromBank(Player player, String taxId, String pin) throws Exception {'
$payInventory = Method-Body $election 'private void payTaxWithInventory(Player player, String taxId, long desired) throws Exception {'

Require-NotContains $artifacts 'cmv4_bank_' 'Artifacts must not query bank tables directly.'
Require-NotContains $artifacts 'bank_pin_hashes' 'Artifacts must not query bank PIN tables directly.'
if ($payBank) {
  Require-NotContains $payBank 'cmv4_bank_' 'ElectionCore bank tax flow must not write bank tables directly.'
}
if ($payInventory) {
  Require-NotContains $payInventory 'cmv4_bank_' 'ElectionCore inventory tax flow must not write bank tables directly.'
}

Throw-IfErrors 'ValidateCopiMineNoDirectEconomySqlOutsideEconomyCore'
