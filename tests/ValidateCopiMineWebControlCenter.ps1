$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$frontendIndex = Join-Path $root 'admin-web\frontend\index.html'
$styleSource = Join-Path $root 'admin-web\frontend\assets\style.css'

$backend = Get-Content -Raw -Encoding UTF8 $backendSource
. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$frontend = Read-FrontendBundle
$style = Read-FrontendStyles
$index = Get-Content -Raw -Encoding UTF8 $frontendIndex
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
Require-Contains $backend '@app.post("/api/elections/control")' 'Backend must expose the disabled web election control endpoint.'
Require-Contains $backend 'HTTPException(status_code=410' 'Web election control must be explicitly disabled with HTTP 410.'
Require-Contains $backend 'server_stats_sync' 'Backend must expose a dedicated server statistics summary.'
Require-Contains $backend '@app.get("/api/server/stats")' 'Backend must have a server statistics endpoint.'
Require-Contains $backend 'pollingStations' 'Election detail must include polling station rows.'
Require-Contains $backend 'voteDeposits' 'Election detail summary must expose deposit/station vote statistics.'

Require-Regex $frontend 'items:[\s\S]*\["stats",' 'Frontend navigation must include a dedicated server statistics tab.'
Require-Contains $frontend 'async function loadStats' 'Frontend must render the server statistics tab.'
Require-Contains $frontend '/api/server/stats' 'Frontend statistics tab must call the server statistics API.'
Require-NotContains $frontend '/api/elections/control' 'Frontend must not expose active web election control actions while the backend route is disabled.'
Require-Contains $frontend 'server-stat-grid' 'Frontend must render a dedicated server statistics surface.'
Require-Contains $frontend 'pollingStations' 'Frontend elections page must show polling station data.'
Require-Contains $frontend 'voteDeposits' 'Frontend elections page must show deposit/station vote stats.'

Require-Contains $style '.server-stat-grid' 'CSS must style the server statistics surface.'
Require-Contains $style '.control-tile' 'CSS must style real-time control tiles.'
Require-Contains $index 'publicStatusGrid' 'Frontend must keep the public status surface mounted in HTML.'

if ($errors.Count -gt 0) {
  throw ("Web control center validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Web control center validation passed: server statistics stay wired, election details remain read-only, and disabled web election controls are explicit.'
