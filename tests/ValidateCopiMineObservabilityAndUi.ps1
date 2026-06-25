$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginSource = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$frontendSource = Join-Path $root 'admin-web\frontend\assets\app.js'
$styleSource = Join-Path $root 'admin-web\frontend\assets\style.css'

$plugin = Get-Content -Raw -Encoding UTF8 $pluginSource
$backend = Get-Content -Raw -Encoding UTF8 $backendSource
$frontend = Get-Content -Raw -Encoding UTF8 $frontendSource
$style = Get-Content -Raw -Encoding UTF8 $styleSource

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    throw $message
  }
}

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) {
    throw $message
  }
}

Require-Contains $plugin 'cmv7_player_activity' 'AdminPlus must persist a player activity timeline for the web panel.'
Require-Contains $plugin 'cmv7_inventory_snapshots' 'AdminPlus must persist online inventory snapshots for live players.'
Require-Contains $plugin 'idx_cmv7_player_activity_player_time' 'Player activity table must be indexed by player/time.'
Require-Contains $plugin 'idx_cmv7_inventory_snapshots_player_time' 'Inventory snapshot table must be indexed by player/time.'
Require-Contains $plugin 'recordPlayerActivity(' 'AdminPlus must have a shared player activity recorder.'
Require-Contains $plugin 'snapshotOnlineInventory(' 'AdminPlus must have a shared live inventory snapshot writer.'
Require-Regex $plugin 'onJoin[\s\S]*recordPlayerActivity[\s\S]*snapshotOnlineInventory' 'Join handling must record activity and snapshot online inventory.'
Require-Regex $plugin 'onQuit[\s\S]*recordPlayerActivity[\s\S]*snapshotOnlineInventory' 'Quit handling must record activity and final inventory.'
Require-Regex $plugin 'onCmd[\s\S]*recordPlayerActivity' 'Player commands must be visible in the player timeline.'
Require-Regex $plugin 'onChat[\s\S]*recordPlayerActivity' 'Player chat must be visible in the player timeline.'
Require-Regex $plugin 'playerAction[\s\S]*recordPlayerActivity[\s\S]*snapshotOnlineInventory' 'Admin player actions must write timeline rows and live snapshots.'
Require-Regex $plugin 'syncAr[\s\S]*recordEconomySnapshot' 'AR sync must write economy snapshot rows for detailed economy view.'

Require-Contains $backend '@app.get("/api/players/{player}/timeline")' 'Backend must expose a combined player timeline endpoint.'
Require-Contains $backend '@app.get("/api/players/{player}/inventory/live")' 'Backend must expose live plugin inventory snapshots.'
Require-Contains $backend '@app.get("/api/elections/detail")' 'Backend must expose detailed election state.'
Require-Contains $backend '@app.get("/api/economy/ares/ledger")' 'Backend must expose AR ledger rows.'
Require-Contains $backend 'plugin_player_activity_sync' 'Backend must read plugin player activity from SQLite.'
Require-Contains $backend 'plugin_inventory_live_sync' 'Backend must read live inventory snapshots from SQLite.'
Require-Contains $backend 'economy_ledger_sync' 'Backend must read detailed AR economy ledger rows.'
Require-Contains $backend 'election_detail_sync' 'Backend must read detailed election candidates, votes, ballots, curators, president and audit.'

Require-Contains $frontend '/api/players/${encodeURIComponent(player)}/timeline' 'Frontend player profile must load the combined timeline.'
Require-Contains $frontend '/api/players/${encodeURIComponent(player)}/inventory/live' 'Frontend player profile must load live inventory snapshots.'
Require-Contains $frontend '/api/elections/detail' 'Frontend elections page must use detailed election API.'
Require-Contains $frontend '/api/economy/ares/ledger' 'Frontend economy page must use detailed AR ledger API.'
Require-Contains $frontend 'activity-timeline' 'Frontend must render a dedicated player activity timeline.'
Require-Contains $frontend 'election-ledger' 'Frontend must render detailed election logs.'
Require-Contains $frontend 'economy-ledger' 'Frontend must render detailed economy ledger.'

Require-Contains $style '.activity-timeline' 'CSS must style player activity logs.'
Require-Contains $style '.ledger-row' 'CSS must style election/economy log rows.'
Require-Contains $style '.inventory-summary' 'CSS must style live inventory summary.'

Write-Host 'Observability/UI validation passed: live inventory, player timeline, detailed elections and economy are wired.'
