. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$artifacts = Read-Utf8 $Paths.Artifacts
$election = Read-Utf8 $Paths.Election

Require-NotRegex $admin 'cleanupProtectedBlockVisuals[\s\S]*Bukkit\.getWorlds\(\)[\s\S]*getEntitiesByClass\(ItemDisplay\.class\)' 'AdminPlus ATM visual cleanup must not scan all worlds.'
Require-NotRegex $artifacts 'cleanupProtectedBlockVisuals[\s\S]*Bukkit\.getWorlds\(\)[\s\S]*getEntitiesByClass\(ItemDisplay\.class\)' 'Artifacts visual cleanup must not scan all worlds.'
Require-NotRegex $election 'cleanupProtectedBlockVisuals[\s\S]*Bukkit\.getWorlds\(\)[\s\S]*getEntitiesByClass\(ItemDisplay\.class\)' 'ElectionCore visual cleanup must not scan all worlds.'

Throw-IfErrors 'ValidateCopiMineCustomBlockVisualsNoWorldScan'
