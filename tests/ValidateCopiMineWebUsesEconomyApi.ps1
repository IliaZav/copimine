. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$mainPy = Read-Utf8 $Paths.MainPy

Require-Contains $mainPy '/api/player/elections/tax/pay' 'Web backend must expose the active election tax payment endpoint.'
Require-Contains $mainPy 'cmv4_bank_accounts' 'Web backend must use service-layer bank storage.'
Require-Contains $mainPy 'CopiMineEconomyCore.ArtifactsBridge' 'Artifact health endpoint must report the EconomyCore bridge.'
Require-NotContains $mainPy 'CopiMineUltimateAdminPlus.ArtifactsBridge' 'Web backend must not advertise the old AdminPlus artifacts bridge.'

Throw-IfErrors 'ValidateCopiMineWebUsesEconomyApi'
