. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'private TaxRecipient taxRecipient(Connection connection, String taxId) throws Exception {' 'Election taxes must resolve a president recipient.'
Require-Contains $text 'ensureBankAccount(connection, recipient.presidentUuid(), recipient.presidentName())' 'Tax payments must credit the president personal bank account.'
Require-Contains $text '"inventory:" + player.getUniqueId()' 'Inventory tax payments must audit the non-bank source.'
Require-NotContains $text 'String treasury = "tax:" + taxId;' 'Taxes must not be routed into the old synthetic treasury account.'

Throw-IfErrors 'ValidateCopiMineElectionTaxToPresident'
