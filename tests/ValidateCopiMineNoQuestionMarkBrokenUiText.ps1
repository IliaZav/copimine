. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$adminYml = Read-Utf8 $Paths.AdminPluginYml
$electionYml = Read-Utf8 $Paths.ElectionPluginYml
$economy = Read-Utf8 $Paths.Economy

Require-NotContains $admin '????' 'AdminPlus still contains broken question-mark UI text.'
Require-NotContains $adminYml '????' 'AdminPlus plugin.yml still contains broken question-mark text.'
Require-NotContains $electionYml '????' 'ElectionCore plugin.yml still contains broken question-mark text.'
Require-NotContains $economy '????' 'EconomyCore still contains broken question-mark UI text.'

Throw-IfErrors 'ValidateCopiMineNoQuestionMarkBrokenUiText'
