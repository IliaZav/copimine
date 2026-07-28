$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/backend/main.py')
$frontend = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web/frontend/assets/js/cabinet-runtime.js')

function Require([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { throw $message }
}

Require $backend 'finish_early' 'The web control API must accept early campaign completion.'
Require $backend 'election.rp.control.early_finish' 'Early completion must be audited separately.'
Require $backend 'current_stage=' 'Early completion must close the active campaign.'
Require $backend 'APPLICATIONS_OPEN' 'Application delivery must accept the private setup stage.'
Require $backend 'election.application.submit' 'Player application submission must be audited.'
Require $frontend 'finish_early' 'Admin UI is missing the early completion action.'
Require $frontend 'elections/application' 'Player application form is not connected to the API.'

Write-Output 'Election web controls contract: PASS'
