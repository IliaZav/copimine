. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList

$economy = Read-Utf8 $Paths.Economy
$artifacts = Read-Utf8 $Paths.Artifacts
$narcotics = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')

Require-NotRegex $economy 'public void onInteract\([^)]*\)\s*\{[\s\S]{0,4000}\.join\(' 'EconomyCore onInteract must not block on CompletableFuture.join().'
Require-NotRegex $economy 'public void onInteract\([^)]*\)\s*\{[\s\S]{0,4000}(CompletableFuture|Future)[^;\n]{0,120}\.get\(' 'EconomyCore onInteract must not block on Future.get().'
Require-NotRegex $artifacts 'InventoryClickEvent[\s\S]{0,3000}\.join\(' 'Artifacts inventory handlers must not block on CompletableFuture.join().'
Require-NotRegex $narcotics 'PlayerInteractEvent[\s\S]{0,3000}\.join\(' 'Narcotics interact handlers must not block on CompletableFuture.join().'

Throw-IfErrors 'ValidateCopiMineNoMainThreadDbWaits'
