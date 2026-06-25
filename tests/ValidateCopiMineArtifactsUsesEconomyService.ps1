. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$pluginYml = Read-Utf8 $Paths.ArtifactsPluginYml

Require-Contains $artifacts 'import me.copimine.economycore.CopiMineEconomyCore;' 'Artifacts must import EconomyCore bridge.'
Require-Contains $artifacts 'CopiMineEconomyCore.ArtifactsBridge' 'Artifacts must use the EconomyCore typed bridge.'
Require-Regex $artifacts 'return\s+\w+\.artifactsBridge\(\);' 'Artifacts must resolve artifacts bridge from EconomyCore.'
Require-NotContains $artifacts 'CopiMineUltimateAdminPlus.ArtifactsBridge' 'Artifacts must not depend on the old AdminPlus bridge.'
Require-Contains $pluginYml 'CopiMineEconomyCore' 'Artifacts plugin.yml must depend on EconomyCore.'

Throw-IfErrors 'ValidateCopiMineArtifactsUsesEconomyService'
