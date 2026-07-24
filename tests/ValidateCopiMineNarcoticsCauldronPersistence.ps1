. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$service = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\cauldron\CauldronBrewingService.java')
$factory = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java')
Require-Contains $service 'restoreOne' 'Cauldron persistence failure must restore the consumed ingredient.'
Require-Contains $service 'saveBrewingState(key, version, frozen)' 'Cauldron state must be persisted before accepting the next state.'
Require-Contains $service 'database save failed' 'Cauldron persistence failures need a clear diagnostic.'
Require-Contains $factory 'restoreOne' 'Narcotic item factory must provide a safe single-item restore.'
Require-Contains $service 'MAX_CACHED_STATES' 'Cauldron cache needs a hard upper bound.'
Require-Contains $service 'entry.getValue().isStale' 'Cauldron preload must discard stale states.'
Require-Contains $service 'loadBrewingStates(MAX_CACHED_STATES)' 'Cauldron preload must request a bounded database result.'
Require-Contains (Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')) 'LIMIT ?' 'Brewing-state database reads must be bounded.'
Throw-IfErrors 'ValidateCopiMineNarcoticsCauldronPersistence'
