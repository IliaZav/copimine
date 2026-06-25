. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$runtime = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
$main = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

Require-Contains $runtime 'clientBridge.visuals().clearVisuals(player);' 'VisualRuntime clear must stop client-side visuals.'
Require-Contains $runtime 'clearServerVisualSurface(player);' 'VisualRuntime clear must clean the server overlay surface.'
Require-Contains $main 'visualRuntime.clear(event.getPlayer());' 'Narcotics main plugin must clear visuals on quit/world-change.'
Require-Contains $main 'visualRuntime.clear(event.getEntity());' 'Narcotics main plugin must clear visuals on death.'

Throw-IfErrors 'ValidateCopiMineVisualRuntimeCleanupAllRoutes'
