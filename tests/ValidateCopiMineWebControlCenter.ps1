$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$frontendSource = Join-Path $root 'admin-web\frontend\assets\app.js'
$styleSource = Join-Path $root 'admin-web\frontend\assets\style.css'

$backend = Get-Content -Raw -Encoding UTF8 $backendSource
$frontend = Get-Content -Raw -Encoding UTF8 $frontendSource
$style = Get-Content -Raw -Encoding UTF8 $styleSource
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains $backend 'class ElectionControlIn' 'Backend must define a typed election control payload.'
Require-Contains $backend '@app.post("/api/elections/control")' 'Backend must expose web election control actions.'
Require-Contains $backend 'election_control_command' 'Backend must map web election actions to the unified plugin command allowlist.'
Require-Contains $backend 'cmultra election start' 'Web election controls must route to the single AdminPlus plugin, not old plugins.'
Require-Contains $backend 'cmultra election pause' 'Web panel must support pausing elections.'
Require-Contains $backend 'cmultra election resume' 'Web panel must support resuming elections.'
Require-Contains $backend 'cmultra election stop' 'Web panel must support ending elections.'
Require-Contains $backend 'cmultra sidebar reload' 'Web panel must support live election panel reload.'
Require-Contains $backend 'cmultra election status' 'Web panel must support election status refresh through AdminPlus.'
Require-Contains $backend 'server_stats_sync' 'Backend must expose a dedicated server statistics summary.'
Require-Contains $backend '@app.get("/api/server/stats")' 'Backend must have a server statistics endpoint.'
Require-Contains $backend 'pollingStations' 'Election detail must include polling station rows.'
Require-Contains $backend 'voteDeposits' 'Election detail summary must expose deposit/station vote statistics.'
Require-Regex $backend 'append_panel_event\("admin-panel", "election_control"' 'Web election actions must be written to panel events.'
Require-Regex $backend 'audit_event\(username, "election.control"' 'Web election actions must be written to audit log.'

Require-Regex $frontend 'items:[\s\S]*\["stats",' 'Frontend navigation must include a dedicated server statistics tab.'
Require-Contains $frontend 'async function loadStats' 'Frontend must render the server statistics tab.'
Require-Contains $frontend '/api/server/stats' 'Frontend statistics tab must call the server statistics API.'
Require-Contains $frontend 'async function electionControl' 'Frontend must provide web election control actions.'
Require-Contains $frontend '/api/elections/control' 'Frontend election controls must call the backend control endpoint.'
Require-Contains $frontend 'election-control-grid' 'Frontend must render a clear election control surface.'
Require-Contains $frontend 'server-stat-grid' 'Frontend must render a dedicated server statistics surface.'
Require-Contains $frontend 'pollingStations' 'Frontend elections page must show polling station data.'
Require-Contains $frontend 'voteDeposits' 'Frontend elections page must show deposit/station vote stats.'

Require-Contains $style '.election-control-grid' 'CSS must style the election control surface.'
Require-Contains $style '.server-stat-grid' 'CSS must style the server statistics surface.'
Require-Contains $style '.control-tile' 'CSS must style real-time control tiles.'

if ($errors.Count -gt 0) {
  throw ("Web control center validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Web control center validation passed: realtime election controls, logs, polling stations, and server stats are wired.'
