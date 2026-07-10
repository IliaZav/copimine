. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy "/api/player/bank/treasury" "Web backend must expose the treasury banking endpoint."
Require-Contains $mainPy '@app.get("/api/player/elections/tax")' "Web backend must expose the player president tax profile route."
Require-Contains $mainPy 'return await bg(player_election_tax_profile_sync, account)' "Web backend must route tax profile reads through the sync worker."
Require-Contains $mainPy 'return await bg(pay_player_election_tax_sync, account, data)' "Web backend must route voluntary president tax payments through the sync worker."
Require-Contains $mainPy "cmv4_bank_accounts" "Web backend must use service-layer bank storage."
Require-Contains $mainPy "CopiMineEconomyCore.ArtifactsBridge" "Artifact health endpoint must report the EconomyCore bridge."
Require-NotContains $mainPy "CopiMineUltimateAdminPlus.ArtifactsBridge" "Web backend must not advertise the old AdminPlus artifacts bridge."

Throw-IfErrors "ValidateCopiMineWebUsesEconomyApi"
