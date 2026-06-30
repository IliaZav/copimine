. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$pluginYml = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\resources\plugin.yml')

Require-NotRegex $pluginYml '(?m)^\s*depend\s*:' 'AuthEffects must not require a hard AuthMe dependency anymore.'
Require-Contains $pluginYml 'softdepend:' 'AuthEffects must use soft dependencies for auth providers.'

Throw-IfErrors 'ValidateCopiMineAuthEffectsNoHardAuthMeDepend'
