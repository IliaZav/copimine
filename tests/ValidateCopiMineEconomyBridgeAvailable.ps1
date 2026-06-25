. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$economy = Read-Utf8 $Paths.Economy

Require-Contains $economy 'getServer().getServicesManager().register(EconomyService.class' 'EconomyService must be registered in ServicesManager.'
Require-Contains $economy 'getServer().getServicesManager().register(BankService.class' 'BankService must be registered in ServicesManager.'
Require-Contains $economy 'getServer().getServicesManager().register(PinService.class' 'PinService must be registered in ServicesManager.'
Require-Contains $economy 'getServer().getServicesManager().register(AtmService.class' 'AtmService must be registered in ServicesManager.'
Require-Contains $economy 'public BankService bankService()' 'EconomyCore must expose bankService bridge access.'
Require-Contains $economy 'public ArtifactsBridge artifactsBridge()' 'EconomyCore must expose artifactsBridge access.'

Throw-IfErrors 'ValidateCopiMineEconomyBridgeAvailable'
