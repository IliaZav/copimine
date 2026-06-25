. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$adminYml = Read-Utf8 $Paths.AdminPluginYml
$electionYml = Read-Utf8 $Paths.ElectionPluginYml
$artifactsYml = Read-Utf8 $Paths.ArtifactsPluginYml

Require-Contains $adminYml 'depend:' 'AdminPlus plugin.yml must declare dependencies.'
Require-Contains $adminYml 'CopiMineEconomyCore' 'AdminPlus must depend on EconomyCore.'
Require-Contains $electionYml 'CopiMineEconomyCore' 'ElectionCore must depend on EconomyCore.'
Require-Contains $artifactsYml 'CopiMineEconomyCore' 'Artifacts must depend on EconomyCore.'

Throw-IfErrors 'ValidateCopiMinePluginYmlEconomyDependencies'
