. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$pluginYml = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\resources\plugin.yml')
$source = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java')
$activePluginDir = Join-Path $root 'minecraft\server\plugins'

Require-Contains $pluginYml 'nLogin' 'AuthEffects must soft-depend on nLogin.'
Require-Contains $source 'com.nickuc.login.api.event.bukkit.auth.AuthenticateEvent' 'AuthEffects must listen for nLogin authenticate events.'
Require-Contains $source 'com.nickuc.login.api.event.bukkit.auth.RegisterEvent' 'AuthEffects must listen for nLogin register events.'
if (-not (Test-Path (Join-Path $activePluginDir 'nLogin.jar'))) {
  $errors.Add('Active plugin bundle must include nLogin.jar.')
}
if (Test-Path (Join-Path $activePluginDir 'AuthMe-5.6.0.jar')) {
  $errors.Add('Active plugin bundle must not keep AuthMe-5.6.0.jar after nLogin migration.')
}

Throw-IfErrors 'ValidateCopiMineAuthUsesNLoginBridge'
