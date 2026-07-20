$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$admin = Get-Content (Join-Path $root 'copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java') -Raw
$artifacts = Get-Content (Join-Path $root 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java') -Raw
$bukkit = Get-Content (Join-Path $root 'minecraft/server/bukkit.yml') -Raw
$spigot = Get-Content (Join-Path $root 'minecraft/server/spigot.yml') -Raw
$paperWorld = Get-Content (Join-Path $root 'minecraft/server/config/paper-world-defaults.yml') -Raw
$paperGlobal = Get-Content (Join-Path $root 'minecraft/server/config/paper-global.yml') -Raw
$properties = Get-Content (Join-Path $root 'minecraft/server/server.properties') -Raw

if ($admin -notmatch 'arPlacedBlockKeys\.contains\(key\)') { throw 'Placed AR blocks are not checked synchronously.' }
if ($admin -notmatch 'boolean reissuePlaced=placed && isValidArCertificationDrop') { throw 'Placed AR Silk Touch reissue path is missing.' }
if ($admin -notmatch 'arPlacedStacks\.put\(key,e\.getItemInHand\(\)\.clone\(\)\)') { throw 'The original official AR stack is not retained.' }
if ($artifacts -notmatch 'FARMER_SWEEP_RADIUS = 2') { throw 'Farmer sweep radius is not explicitly five by five.' }
if ($artifacts -notmatch 'var5 <= FARMER_SWEEP_RADIUS' -or $artifacts -notmatch 'var6 <= FARMER_SWEEP_RADIUS') { throw 'Farmer sweep does not cover both inclusive axes.' }
if ($bukkit -notmatch '(?m)^\s*monsters:\s*70\s*$' -or $bukkit -notmatch '(?m)^\s*monster-spawns:\s*1\s*$') { throw 'Bukkit monster spawning is still throttled.' }
if ($spigot -notmatch '(?m)^\s*mob-spawn-range:\s*8\s*$' -or $spigot -notmatch '(?m)^\s*nerf-spawner-mobs:\s*false\s*$') { throw 'Spigot mob spawning is not vanilla-like.' }
if ($paperWorld -notmatch '(?m)^\s*per-player-mob-spawns:\s*false\s*$' -or $paperGlobal -notmatch '(?m)^\s*per-player-mob-spawns:\s*false\s*$') { throw 'Paper still splits mob caps per player.' }
if ($properties -notmatch '(?m)^simulation-distance=10\s*$' -or $properties -notmatch '(?m)^view-distance=10\s*$') { throw 'Server distances are still reduced.' }

Write-Host 'ValidateCopiMineGameplayFixes: PASS'
