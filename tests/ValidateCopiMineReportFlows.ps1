$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginYml = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\plugin.yml')
$pluginJava = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java')
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')
$adminUi = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\cabinet-runtime.js')
$playerUi = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\frontend\assets\js\player\account-pages.js')

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) {
    throw $message
  }
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    throw $message
  }
}

Require-Contains $pluginYml 'reporta:' 'plugin.yml must register /reporta.'
Require-Contains $pluginYml 'aliases: [bugreporta, bugreport]' 'plugin.yml must keep bug-report aliases for /reporta.'
Require-Contains $pluginJava 'handleReportaCommand' 'AdminPlus must keep the dedicated /reporta handler.'
Require-Contains $pluginJava 'handleReport(sender,args);' 'Manual /reporta without bug context must fall back to normal /report.'
Require-Contains $pluginJava 'PendingBugReport pending=activePendingBugReport(p)' 'Reporta flow must check pending bug context with TTL enforcement.'
Require-Contains $pluginJava 'ClickEvent.Action.SUGGEST_COMMAND,"/reporta "' 'Player bug notification must suggest /reporta in chat.'
Require-Contains $pluginJava 'BUG_REPORT_CREATE' 'Bug-report creation must be audited.'
Require-Contains $pluginJava 'PLAYER_BUG_DETECTED' 'Player bug detection must emit a plugin event.'
Require-Contains $pluginJava 'player.sendTitle(c("&6' 'Player bug notification must use a title call.'
Require-Regex $pluginJava '\\"technical\\":\{\\"exceptionClass\\":' 'Bug-report payload must include technical diagnostics for admin-side triage.'
Require-Regex $pluginJava 'notifyPlayerBug[\s\S]*warn\(player,"Error code:.*errorSummary\)' 'Player-facing bug message must expose only a code and safe summary.'
Require-Regex $pluginJava 'notifyPlayerBug[\s\S]*exceptionClass' 'Technical exception class must remain available for admin-side triage.'

Require-Contains $backend 'def public_player_report' 'Backend must shape a reduced player-facing report payload.'
Require-Contains $backend 'for key in ("reportKind", "errorCode", "errorSummary", "capturedAt")' 'Player-facing bug metadata must keep only safe bug fields.'
Require-Contains $backend 'row["attached_events"] = []' 'Player-facing report payload must strip attached events.'
Require-Contains $backend 'row["timeline"] = []' 'Player-facing report payload must strip admin timeline details.'
Require-Contains $backend '@app.get("/api/player/reports")' 'Backend must expose player report history.'
Require-Contains $backend '@app.post("/api/player/reports")' 'Backend must expose player report creation.'
Require-Contains $backend '@app.get("/api/reports")' 'Backend must expose admin report queue.'
Require-Contains $backend '@app.post("/api/plugin/tickets"' 'Plugin bug reports must ingest through the plugin-key endpoint.'
Require-Contains $backend 'Depends(require_plugin_key)' 'Plugin bug reports must stay protected by plugin key auth.'

Require-Contains $adminUi 'safeApi("/api/reports", { reports: [] })' 'Admin requests page must load report queue from backend.'
Require-Contains $adminUi 'reportQueueRows' 'Admin requests page must normalize report queue rows before rendering.'
Require-Regex $adminUi '\{ key: "errorCode", label: "[^"]+" \}' 'Admin requests table must show bug codes.'
Require-Regex $adminUi '\{ key: "errorSummary", label: "[^"]+" \}' 'Admin requests table must show bug summaries.'

Require-Contains $playerUi 'safeApi("/api/player/reports", { reports: [] })' 'Player settings must load player-safe report history.'
Require-Contains $playerUi 'submitPlayerSupportReport' 'Player settings must allow creating a normal report from the site.'
Require-Contains $playerUi 'renderReportList' 'Player settings must render a report history list.'
Require-Contains $playerUi 'report.errorCode' 'Player report list must show bug codes when present.'
Require-Contains $playerUi 'report.errorSummary' 'Player report list must show safe bug summaries when present.'

Write-Host 'Report/reporta validation passed: plugin command flow, admin diagnostics, player-safe redaction and site rendering are aligned.'
