. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$worldCorePath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')) 'copimine-world-core\src\me\copimine\worldcore\CopiMineWorldCore.java'
$worldCore = Read-Utf8 $worldCorePath

Require-Contains $worldCore 'if (distance > overworldLimit.radius())' 'WorldCore must clamp immediately when player crosses the real radius.'
Require-Contains $worldCore 'if (distance >= warningThreshold(overworldLimit))' 'WorldCore warning should start before the border, not after it.'
Require-NotContains $worldCore 'radius() + overworldLimit.warningDistance()' 'WorldCore must not allow walking outside radius by warning distance.'
Require-Contains $worldCore 'private Location findSafeLocation(World world, Location origin)' 'WorldCore must use safe-location search for teleports and border correction.'
Require-Contains $worldCore 'private boolean isSafeStandingLocation(World world, int x, int y, int z)' 'WorldCore must validate floor and headroom before teleport.'

Throw-IfErrors 'ValidateCopiMineWorldCoreStrictBorder'
