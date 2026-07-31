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
Require $backend '_repair_rp_candidate_rows' 'Live election reads and completion must repair legacy candidate roster rows.'
Require $backend "UPDATE rounds SET status='COUNTED'" 'Web completion must close the active round and preserve its winner.'
Require $backend 'manual_winner_uuid=%s' 'Web completion must preserve an explicit winner in election history.'
Require $backend 'election_voting_blocks(id,election_id,world,x,y,z,active,created_at,created_by,updated_at) VALUES(%s,%s,%s,%s,%s,%s,1,%s,%s,%s)' 'Voting-block creation must bind all three coordinates and the complete block row.'
Require $frontend 'finish_early' 'Admin UI is missing the early completion action.'
Require $frontend 'elections/application' 'Player application form is not connected to the API.'
Require $frontend 'const applicationRows = isPanelAdminRole() ? asArray(data.applications) : [];' 'Player elections renderer must not reference undefined admin application rows.'
Require $frontend 'const votingBlocks = isPanelAdminRole() ? asArray(data.votingBlocks) : [];' 'Player elections renderer must not reference undefined admin voting blocks.'
if (([regex]::Matches($frontend, 'id="rpWinnerUuid"')).Count -lt 2) {
    throw 'Both RP admin panel renderers must expose the tie-break winner UUID field.'
}

Write-Output 'Election web controls contract: PASS'
