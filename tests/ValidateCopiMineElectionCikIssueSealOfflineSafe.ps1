. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'officialRestore' 'Offline seal issuance must queue the seal for later delivery.'
Require-Contains $text 'if (target == null)' 'CIK seal issuance must branch safely for offline players.'
Require-Contains $text 'Bukkit.getOfflinePlayer' 'Offline seal issuance must resolve offline players without requiring them online.'

Throw-IfErrors 'ValidateCopiMineElectionCikIssueSealOfflineSafe'
