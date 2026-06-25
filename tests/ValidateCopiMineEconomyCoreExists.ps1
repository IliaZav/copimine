. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy
$pluginYml = Read-Utf8 $Paths.EconomyPluginYml

Require-Contains $pluginYml 'name: CopiMineEconomyCore' 'CopiMineEconomyCore plugin.yml must exist.'
Require-Contains $economy 'class CopiMineEconomyCore' 'Economy core main class must exist.'
Require-Contains $economy 'interface EconomyService' 'EconomyService interface must be declared.'
Require-Contains $economy 'interface BankService' 'BankService interface must be declared.'
Require-Contains $economy 'interface PinService' 'PinService interface must be declared.'
Require-Contains $economy 'interface AtmService' 'AtmService interface must be declared.'
Require-Contains $economy 'interface LedgerService' 'LedgerService interface must be declared.'

Throw-IfErrors 'ValidateCopiMineEconomyCoreExists'
