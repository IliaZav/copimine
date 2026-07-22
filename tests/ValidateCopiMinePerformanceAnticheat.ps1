$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$server = Join-Path $root 'minecraft\server'
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$errors = New-Object System.Collections.Generic.List[string]

function Read-Text([string]$path) {
  if (-not (Test-Path $path)) { return '' }
  (Get-Content -Raw -Encoding UTF8 $path) -replace "`r", ''
}

function Require-File([string]$path, [string]$message) {
  if (-not (Test-Path $path)) { $script:errors.Add($message) }
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

$grimJar = Join-Path $server 'plugins\GrimAC.jar'
$grimConfig = Join-Path $server 'plugins\GrimAC\config\en.yml'
$grimConfigRu = Join-Path $server 'plugins\GrimAC\config\ru.yml'
$grimChecks = Join-Path $server 'plugins\GrimAC\checks.yml'
$grimPunishments = Join-Path $server 'plugins\GrimAC\punishments\en.yml'
$grimPunishmentsRu = Join-Path $server 'plugins\GrimAC\punishments\ru.yml'
$serverProperties = Read-Text (Join-Path $server 'server.properties')
$bukkit = Read-Text (Join-Path $server 'bukkit.yml')
$spigot = Read-Text (Join-Path $server 'spigot.yml')
$paperGlobal = Read-Text (Join-Path $server 'config\paper-global.yml')
$paperWorld = Read-Text (Join-Path $server 'config\paper-world-defaults.yml')
$purpur = Read-Text (Join-Path $server 'purpur.yml')
$entityClearer = Read-Text (Join-Path $server 'plugins\EntityClearer\config.yml')
$tab = Read-Text (Join-Path $server 'plugins\TAB\config.yml')
$backend = Read-Text $backendSource
$frontend = Read-FrontendBundle
$style = Read-FrontendStyles

Require-File $grimJar 'GrimAC.jar must be installed as the single anticheat plugin jar.'
if (Test-Path $grimJar) {
  $size = (Get-Item $grimJar).Length
  if ($size -lt 5000000) { $errors.Add('GrimAC.jar looks too small to be a real release artifact.') }
}
Require-File $grimConfig 'GrimAC config/en.yml must be pre-created for deploy-by-folder replacement.'
Require-File $grimConfigRu 'GrimAC config/ru.yml must be pre-created for deploy-by-folder replacement.'
Require-File $grimChecks 'GrimAC checks.yml compatibility profile must be pre-created for deploy-by-folder replacement.'
Require-File $grimPunishments 'GrimAC punishments/en.yml must be pre-created for deploy-by-folder replacement.'
Require-File $grimPunishmentsRu 'GrimAC punishments/ru.yml must be pre-created for deploy-by-folder replacement.'

$punishments = Read-Text $grimPunishments
$punishmentsRu = Read-Text $grimPunishmentsRu
$config = Read-Text $grimConfig
$checks = Read-Text $grimChecks
Require-Regex $config '(?m)^experimental-checks:\s*false$' 'GrimAC experimental checks must stay disabled for stability.'
Require-Regex $config '(?m)^max-transaction-time:\s*60$' 'GrimAC transaction timeout should be bounded.'
Require-Regex $config '(?m)^packet-spam-threshold:\s*100$' 'GrimAC packet spam threshold should be explicit.'
Require-Contains $config '- ".*"' 'GrimAC client brand messages should be suppressed for all clients.'
Require-Regex $checks '(?m)^experimental-checks:\s*false$' 'GrimAC checks.yml must keep experimental checks disabled.'
Require-Contains $punishments 'CopiMineSilentMonitor' 'GrimAC punishments must use the CopiMine silent monitoring profile.'
Require-Contains $punishmentsRu 'CopiMineSilentMonitor' 'GrimAC Russian punishments must use the CopiMine silent monitoring profile.'
Require-Contains $punishments '[log]' 'GrimAC punishments should keep console logs for the website without chat/screen alerts.'
Require-Contains $punishmentsRu '[log]' 'GrimAC Russian punishments should keep console logs for the website without chat/screen alerts.'
if (($punishments + "`n" + $punishmentsRu) -match '\[alert\]|\[webhook\]|\[proxy\]|minecraft:say|title\s+%player%|tellraw|broadcast|actionbar') {
  $errors.Add('GrimAC punishments must not contain in-game alert/chat/screen/proxy/webhook commands.')
}

Require-Regex $serverProperties '(?m)^max-players=50$' 'max-players must be raised to 50 for the release target.'
Require-Regex $serverProperties '(?m)^view-distance=10$' 'view-distance must remain at the vanilla value of 10.'
Require-Regex $serverProperties '(?m)^simulation-distance=5$' 'simulation-distance must remain at the release value of 5.'
Require-Regex $serverProperties '(?m)^entity-broadcast-range-percentage=100$' 'entity broadcast range must stay at the full tracking range so mobs do not appear to disappear.'
Require-Regex $serverProperties '(?m)^network-compression-threshold=512$' 'network compression threshold should remain CPU-friendly.'
Require-Regex $serverProperties '(?m)^sync-chunk-writes=false$' 'sync chunk writes must be disabled.'

Require-Regex $paperGlobal '(?m)^\s*player-max-chunk-load-rate:\s*45\.0$' 'Paper chunk load rate should be tightened.'
Require-Regex $paperGlobal '(?m)^\s*player-max-chunk-send-rate:\s*35\.0$' 'Paper chunk send rate should be tightened.'
Require-Regex $paperGlobal '(?m)^\s*max-auto-save-chunks-per-tick:\s*3$' 'Paper autosave chunks per tick should be tightened.'
Require-Regex $paperWorld '(?m)^\s*redstone-implementation:\s*ALTERNATE_CURRENT$' 'Paper redstone should use Alternate Current for MSPT stability.'
Require-Regex $paperWorld '(?m)^\s*armor-stands:\s*\n\s*do-collision-entity-lookups:\s*false\s*\n\s*tick:\s*false' 'Armor stand ticking/collision lookups should be disabled by default.'
Require-Regex $paperWorld '(?m)^\s*delay-chunk-unloads-by:\s*15s$' 'Chunk unload delay should avoid unload/load jitter.'
Require-Regex $paperWorld '(?m)^\s*max-auto-save-chunks-per-tick:\s*3$' 'World autosave chunks per tick should be tightened.'
Require-Regex $spigot '(?m)^\s*mob-spawn-range:\s*8$' 'Mob spawn range must remain at the vanilla value of 8.'
Require-Regex $spigot '(?m)^\s*hopper-transfer:\s*16$' 'Hopper transfer interval should be relaxed.'
Require-Regex $spigot '(?m)^\s*hopper-check:\s*16$' 'Hopper check interval should be relaxed.'
Require-Regex $bukkit '(?m)^\s*monsters:\s*70$' 'Monster spawn limit must remain at the vanilla value of 70.'
Require-Regex $bukkit '(?m)^\s*animals:\s*10$' 'Animal spawn limit must remain at the vanilla value of 10.'
Require-Regex $purpur '(?m)^\s*tps-catchup:\s*false$' 'Purpur TPS catchup should be disabled.'
Require-Regex $entityClearer '(?m)^\s*actionbar-message:\s*""$' 'EntityClearer warnings must not appear in actionbar.'
Require-Regex $entityClearer '(?m)^\s*actionbar-completed-message:\s*""$' 'EntityClearer completion must not appear in actionbar.'
Require-Regex $tab '(?m)^\s*default-refresh-interval:\s*2000$' 'TAB placeholder refresh should be relaxed to reduce per-player churn.'

Require-Contains $backend 'def anticheat_status_sync' 'Backend must expose anticheat status collection.'
Require-Contains $backend '@app.get("/api/anticheat/status")' 'Backend must expose an anticheat status endpoint.'
Require-Contains $backend 'GrimAC' 'Backend anticheat status must detect GrimAC.'
Require-Contains $frontend '["anticheat",' 'Frontend navigation must include an anticheat tab.'
Require-Contains $frontend 'async function loadAnticheat' 'Frontend must render the anticheat page.'
Require-Contains $frontend '/api/anticheat/status' 'Frontend anticheat page must call the backend endpoint.'
Require-Contains $frontend 'anticheat-events' 'Frontend must show anticheat events/logs.'
Require-Contains $style '.anticheat-signal' 'CSS must style anticheat signal rows.'

if ($errors.Count -gt 0) {
  throw ("Performance/anticheat validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Performance/anticheat validation passed: GrimAC is installed silently, safe performance limits remain, and vanilla mob spawning is preserved.'
