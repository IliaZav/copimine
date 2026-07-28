$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Get-Content -Raw (Join-Path $root 'admin-web/backend/main.py')
$migration = Get-Content -Raw (Join-Path $root 'db/migrations/20260724_014_rp_election_audit_hardening.sql')
$java = Get-Content -Raw (Join-Path $root 'copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java')

function Require([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { throw $message }
}

Require $migration 'uq_elections_single_active_rp' 'Missing one-active-RP unique index migration.'
Require $migration 'ROW_NUMBER() OVER' 'Migration must repair duplicate active RP rows before creating the index.'
Require $backend 'uq_elections_single_active_rp' 'Backend bootstrap does not install the one-active-RP index.'
Require $java 'uq_elections_single_active_rp' 'ElectionCore bootstrap does not install the one-active-RP index.'
Require $backend 'WHERE rc.election_id=%s AND rc.round_no=%s AND rc.active=1' 'Player result aggregation is not constrained to the current round.'
Require $backend 'GROUP BY rc.candidate_uuid ORDER BY votes DESC,name ASC LIMIT %s' 'Admin result aggregation is not constrained to the current round.'
Require $backend 'WHERE election_id=%s AND round_no=%s' 'Election read model has no current-round filter.'
Require $backend 'if not world:' 'Backend accepts a whitespace-only voting-block world.'
Require $java 'if (world.isBlank())' 'ElectionCore accepts a whitespace-only voting-block world.'
Require $java 'SELECT election_id,world,x,y,z,active FROM election_voting_blocks WHERE id=? FOR UPDATE' 'Block disable does not lock and inspect its owning campaign.'
Require $java 'SELECT current_stage,status FROM elections WHERE id=? AND active=1' 'Block creation does not revalidate the campaign inside its transaction.'
Require $java 'SELECT id,kind,linked_id FROM protected_blocks WHERE world=? AND x=? AND y=? AND z=? AND active=1' 'Block creation does not lock the coordinate protection row.'
Require $java 'parseUuidOrNull' 'Election finish does not safely handle malformed stored candidate UUIDs.'
Require $java 'openDirectVoteMenuAsync' 'Player voting still uses a synchronous database path.'
Require $backend 'def election_now_ms' 'Election writes must use the millisecond clock shared with ElectionCore.'
Require $backend 'now = election_now_ms()' 'At least one election writer still uses second-resolution timestamps.'
Require $java "JOIN candidate_applications a ON a.id=c.application_id AND a.admin_status='APPROVED'" 'Voting menu can show a rejected application.'
Write-Output 'Election audit hardening contract passed.'
