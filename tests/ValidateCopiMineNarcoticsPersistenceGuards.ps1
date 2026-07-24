. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$overdose = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java')
Require-Contains $overdose 'MAX_PRELOAD_RETRIES' 'Overdose preload needs a bounded retry policy.'
Require-Contains $overdose 'preloadFailures' 'Overdose preload must track failed attempts per player.'
Require-Contains $overdose 'preloadFailures.remove(playerUuid)' 'Successful preload must clear retry state.'
Throw-IfErrors 'ValidateCopiMineNarcoticsPersistenceGuards'
