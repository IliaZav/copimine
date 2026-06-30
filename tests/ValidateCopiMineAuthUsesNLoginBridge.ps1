. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$pluginYml = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\resources\plugin.yml')
$source = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java')

Require-Contains $pluginYml 'nLogin' 'AuthEffects must soft-depend on nLogin.'
Require-Contains $pluginYml 'nLogin-AuthMeAPI' 'AuthEffects must soft-depend on the nLogin AuthMe compatibility bridge.'
Require-Contains $source 'com.nickuc.login.api.event.bukkit.auth.AuthenticateEvent' 'AuthEffects must listen for nLogin authenticate events.'
Require-Contains $source 'com.nickuc.login.api.event.bukkit.auth.RegisterEvent' 'AuthEffects must listen for nLogin register events.'

Throw-IfErrors 'ValidateCopiMineAuthUsesNLoginBridge'
