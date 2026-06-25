. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldPlugin = Join-Path $root 'copimine-world-core\plugin.yml'
$worldJava = Join-Path (Split-Path $worldPlugin -Parent) 'src\me\copimine\worldcore\CopiMineWorldCore.java'
$admin = Read-Utf8 $Paths.Admin
$pluginYml = Read-Utf8 $worldPlugin
$java = Read-Utf8 $worldJava

Require-Contains $pluginYml 'name: CopiMineWorldCore' 'WorldCore plugin.yml must declare CopiMineWorldCore.'
Require-Contains $pluginYml 'cmworld:' 'WorldCore must register /cmworld.'
Require-Contains $java 'class CopiMineWorldCore' 'WorldCore main class must exist.'
Require-Contains $java 'openAdminWorldHub' 'WorldCore must expose admin hub for AdminPlus delegation.'
Require-Contains $admin 'openWorldCoreHub' 'AdminPlus must delegate world GUI into WorldCore.'

Throw-IfErrors 'ValidateCopiMineWorldCoreExists'
