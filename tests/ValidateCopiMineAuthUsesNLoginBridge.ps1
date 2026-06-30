. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$pluginYml = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\resources\plugin.yml')
$source = Read-Utf8 (Join-Path $root 'minecraft\server\plugins\AuthEffects\src\main\java\me\serverrp\autheffects\AuthEffectsPlugin.java')
$activePluginDir = Join-Path $root 'minecraft\server\plugins'

Require-Contains $pluginYml 'AuthMe' 'AuthEffects must soft-depend on AuthMe.'
Require-Contains $source 'fr.xephi.authme.events.LoginEvent' 'AuthEffects must listen for AuthMe login events.'
Require-Contains $source 'fr.xephi.authme.events.RegisterEvent' 'AuthEffects must listen for AuthMe register events.'
if (-not (Test-Path (Join-Path $activePluginDir 'AuthMe-5.6.0.jar'))) {
  $errors.Add('Active plugin bundle must include AuthMe-5.6.0.jar.')
}
if (Test-Path (Join-Path $activePluginDir 'nLogin.jar')) {
  $errors.Add('Active plugin bundle must not keep nLogin.jar after AuthMe rollback.')
}

Throw-IfErrors 'ValidateCopiMineAuthUsesNLoginBridge'
