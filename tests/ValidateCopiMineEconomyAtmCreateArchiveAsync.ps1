. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Economy

Require-Contains $text 'private String createBankAtmFromTargetAsync(Player player) throws Exception {' 'EconomyCore must have async ATM create wrapper.'
Require-Contains $text 'dbFuture("create atm"' 'ATM create wrapper must move DB work off the main thread.'
Require-Contains $text 'private String archiveBankAtmAsync(Player actor, String atmId) throws Exception {' 'EconomyCore must have async ATM archive wrapper.'
Require-Contains $text 'dbFuture("archive atm"' 'ATM archive wrapper must move DB work off the main thread.'
Require-Contains $text 'dbFuture("load atm visual cleanup"' 'ATM visual cleanup must load DB state asynchronously.'

Throw-IfErrors 'ValidateCopiMineEconomyAtmCreateArchiveAsync'
