. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$artifactsCode = [regex]::Replace($artifacts, '(?s:/\*.*?\*/)|(?<!:)//[^\r\n]*', '')

Require-Contains $artifacts 'import me.copimine.economycore.CopiMineEconomyCore;' 'Artifacts must import EconomyCore directly.'
Require-Contains $artifacts 'CopiMineEconomyCore.ArtifactsBridge' 'Artifacts must use the EconomyCore bridge contract directly.'
Require-NotContains $artifactsCode 'CopiMineUltimateAdminPlus' 'Artifacts must not depend on AdminPlus for economy integration.'

Throw-IfErrors 'ValidateCopiMineArtifactsUsesEconomyCoreDirectly'
