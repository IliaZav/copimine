. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldCore = Read-Utf8 (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')

Require-Contains $worldCore 'public void onQuit(PlayerQuitEvent event)' 'WorldCore must clean warnedOutside on player quit.'
Require-Contains $worldCore 'warnedOutside.remove(event.getPlayer().getUniqueId());' 'WorldCore quit handler must remove player warning state.'

Throw-IfErrors 'ValidateCopiMineWorldCoreQuitCleanup'
