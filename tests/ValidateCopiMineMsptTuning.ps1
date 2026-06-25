$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$server = Join-Path $root 'minecraft\server'
$errors = New-Object System.Collections.Generic.List[string]

function Read-Text([string]$relative) {
  (Get-Content -Raw -Encoding UTF8 (Join-Path $server $relative)) -replace "`r", ''
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

$serverProperties = Read-Text 'server.properties'
$paperGlobal = Read-Text 'config\paper-global.yml'
$paperWorld = Read-Text 'config\paper-world-defaults.yml'
$pufferfish = Read-Text 'pufferfish.yml'
$spigot = Read-Text 'spigot.yml'
$purpur = Read-Text 'purpur.yml'
$coreprotect = Read-Text 'plugins\CoreProtect\config.yml'
$tab = Read-Text 'plugins\TAB\config.yml'
$entityClearer = Read-Text 'plugins\EntityClearer\config.yml'

Require-Regex $serverProperties '(?m)^max-players=50$' 'Server max-players should be raised to 50 for the release target.'
Require-Regex $serverProperties '(?m)^view-distance=8$' 'Server view-distance should be raised to 8 for the release target.'
Require-Regex $serverProperties '(?m)^simulation-distance=6$' 'Server simulation-distance should start at 6 before any live upgrade to 8.'
Require-Regex $serverProperties '(?m)^sync-chunk-writes=false$' 'sync-chunk-writes must stay disabled.'
Require-Regex $serverProperties '(?m)^network-compression-threshold=512$' 'Network compression threshold should be tuned to reduce CPU spikes.'

Require-Regex $paperGlobal '(?m)^\s*player-max-chunk-load-rate:\s*45\.0$' 'Paper chunk load rate should be capped.'
Require-Regex $paperGlobal '(?m)^\s*player-max-chunk-send-rate:\s*35\.0$' 'Paper chunk send rate should be capped.'
Require-Regex $paperGlobal '(?m)^\s*max-auto-save-chunks-per-tick:\s*3$' 'Paper autosave chunks per tick should be capped.'
Require-Regex $paperGlobal '(?m)^\s*max-per-tick:\s*10$' 'Paper player autosave max-per-tick should be bounded.'
Require-Regex $paperGlobal '(?m)^\s*rate:\s*600$' 'Paper player autosave rate should be bounded.'

Require-Regex $paperWorld '(?m)^\s*update-pathfinding-on-block-update:\s*false$' 'Pathfinding block update recalculation should be disabled.'
Require-Regex $paperWorld '(?m)^\s*container-update:\s*3$' 'Container updates should be less frequent.'
Require-Regex $paperWorld '(?m)^\s*mob-spawner:\s*2$' 'Mob spawner tick rate should be relaxed.'
Require-Regex $paperWorld '(?m)^\s*experience_orb:\s*16$' 'Experience orb save limit should be capped.'

Require-Regex $pufferfish '(?m)^\s*inactive-goal-selector-throttle:\s*true$' 'Pufferfish inactive goal selector throttle should be enabled.'
Require-Regex $pufferfish '(?m)^\s*enabled:\s*true$' 'Pufferfish DAB should be enabled.'
Require-Regex $pufferfish '(?m)^tps-catchup:\s*false$' 'TPS catchup should be disabled to avoid catch-up spikes.'
Require-Regex $pufferfish '(?m)^\s*max-loads-per-tick:\s*4$' 'Projectile chunk loads per tick should be low.'

Require-Regex $spigot '(?m)^\s*tick-inactive-villagers:\s*false$' 'Inactive villager ticking should be disabled.'
Require-Regex $spigot '(?m)^\s*nerf-spawner-mobs:\s*true$' 'Spawner mobs should be nerfed for MSPT.'

Require-Regex $coreprotect '(?m)^check-updates:\s*false$' 'CoreProtect update checks should be disabled.'
Require-Regex $coreprotect '(?m)^verbose:\s*false$' 'CoreProtect verbose rollback logging should be disabled.'
Require-Regex $coreprotect '(?m)^hopper-transactions:\s*false$' 'CoreProtect hopper transaction logging should be disabled.'
Require-Regex $coreprotect '(?m)^item-pickups:\s*false$' 'CoreProtect item pickup logging should be disabled.'
Require-Regex $coreprotect '(?m)^leaf-decay:\s*false$' 'CoreProtect leaf decay logging should be disabled.'

Require-Regex $tab '(?m)^\s*default-refresh-interval:\s*2000$' 'TAB default placeholder refresh should be relaxed.'
Require-Regex $tab '(?m)^permission-refresh-interval:\s*3000$' 'TAB permission refresh should be relaxed.'
Require-Regex $purpur '(?m)^\s*tick-interval:\s*40$' 'Purpur TPS/RAM bar intervals should be relaxed.'

Require-Regex $entityClearer '(?m)^\s*enabled:\s*true$' 'EntityClearer low TPS mode should be enabled.'
Require-Regex $entityClearer '(?m)^\s*threshold:\s*18$' 'EntityClearer low TPS threshold should protect MSPT before severe lag.'
Require-Regex $entityClearer '(?m)^\s*count:\s*8$' 'EntityClearer nearby cluster threshold should be tightened.'

if ($errors.Count -gt 0) {
  throw ("MSPT tuning validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'MSPT tuning validation passed: server, Paper/Pufferfish/Spigot, CoreProtect, TAB, and EntityClearer configs are tuned for the 50-player target.'
