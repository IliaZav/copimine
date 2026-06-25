. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldCore = Read-Utf8 (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')

Require-Contains $worldCore 'openWorldCloseConfirmMenu(Player player, boolean nether, int playersInside)' 'WorldCore must show a GUI confirmation menu before closing Nether or End with players inside.'
Require-Contains $worldCore 'gui:confirm:close:nether' 'WorldCore must have a Nether close confirmation action.'
Require-Contains $worldCore 'gui:confirm:close:end' 'WorldCore must have an End close confirmation action.'
Require-Contains $worldCore 'gui:close-confirm:cancel' 'WorldCore close confirmation GUI must support cancel/back.'

Throw-IfErrors 'ValidateCopiMineWorldCoreGuiCloseConfirm'
