. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldCore = Read-Utf8 (Join-Path $root 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java')

Require-Contains $worldCore 'if (isSafeStandingLocation(world, originX, fallbackY, originZ))' 'WorldCore final highest-block fallback must verify safety before teleporting.'
Require-Contains $worldCore 'WorldCore failed to find a safe location in world' 'WorldCore must log and cancel when no safe teleport target exists.'
Require-Contains $worldCore 'Location spawnSafe = safeLocationAt(world, world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockZ());' 'WorldCore must try a safe spawn fallback before giving up.'

Throw-IfErrors 'ValidateCopiMineWorldCoreFinalFallbackSafe'
