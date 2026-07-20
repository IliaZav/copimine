$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Get-Content -Raw (Join-Path $root 'admin-web/backend/main.py')
$runtime = Get-Content -Raw (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js')

function Assert-Contains([string]$text, [string]$needle, [string]$message) {
    if ($text.IndexOf($needle, [StringComparison]::Ordinal) -lt 0) { throw $message }
}

Assert-Contains $backend '/api/dashboard/insights' 'Dashboard insights route is missing.'
Assert-Contains $backend '/api/dashboard/activity' 'Dashboard activity route is missing.'
Assert-Contains $backend 'purchasesByDay' 'Purchase trend data is missing.'
Assert-Contains $backend 'sessionActivityByDay' 'Session activity data is missing.'
Assert-Contains $runtime '/api/dashboard/insights' 'Dashboard does not load real insights.'
Assert-Contains $runtime 'purchasesByDay' 'Dashboard does not render purchase trend.'
Assert-Contains $runtime 'sessionActivityByDay' 'Dashboard does not render session activity.'
Assert-Contains $runtime 'passwordSet' 'Admin table does not expose safe password status.'

Write-Host 'CopiMine dashboard insights contract: PASS'
