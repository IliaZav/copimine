. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy "/api/player/bank/treasury" "Web backend must expose the treasury banking endpoint."
Require-Contains $mainPy '@app.get("/api/player/elections/tax")' "Removed president tax profile route must still exist as an explicit disabled endpoint."
Require-Contains $mainPy 'status_code=410' "Removed president tax routes must be hard-disabled explicitly."
Require-NotContains $mainPy "return await bg(pay_player_election_tax_sync, account, data)" "Web backend must not keep the old active president tax payment flow."
Require-Contains $mainPy "cmv4_bank_accounts" "Web backend must use service-layer bank storage."
Require-Contains $mainPy "CopiMineEconomyCore.ArtifactsBridge" "Artifact health endpoint must report the EconomyCore bridge."
Require-NotContains $mainPy "CopiMineUltimateAdminPlus.ArtifactsBridge" "Web backend must not advertise the old AdminPlus artifacts bridge."

Throw-IfErrors "ValidateCopiMineWebUsesEconomyApi"
