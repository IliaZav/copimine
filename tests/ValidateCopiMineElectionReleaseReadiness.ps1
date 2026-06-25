$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$text = Get-Content -Raw -Encoding UTF8 $source
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-NotContains([string]$needle, [string]$message) {
  if ($text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'ElectionGateRow' 'Missing release gate model for stage safety.'
Require-Contains 'openElectionReleaseBoard' 'Missing release readiness GUI for the election lifecycle.'
Require-Contains 'openElectionCeremony' 'Missing RP ceremony/director GUI.'
Require-Contains 'releaseGateRows' 'Missing release gate row builder.'
Require-Contains 'requireElectionGate' 'Missing hard gate validation before risky stage transitions.'
Require-Contains 'expireUndepositedBallotSessions' 'Missing cleanup for sealed-but-not-deposited ballot sessions.'
Require-Contains 'nextReleaseTarget' 'Missing automatic target stage for readiness board.'
Require-Contains 'broadcastElectionMoment' 'Missing polished stage/RP broadcast helper.'

Require-Regex 'openElections[\s\S]*open:election-release[\s\S]*open:election-ceremony' 'Main election menu must expose release readiness and RP ceremony.'
Require-Regex 'openElectionLifecycle[\s\S]*open:election-release[\s\S]*open:election-ceremony' 'Lifecycle wizard must expose release readiness and RP ceremony.'
Require-Regex 'openChairPanel[\s\S]*open:election-release[\s\S]*open:election-ceremony' 'Chair panel must expose release readiness and RP ceremony.'
Require-Regex 'handle\(Player p[\s\S]*open:election-release[\s\S]*openElectionReleaseBoard' 'Release readiness GUI must be wired into click handler.'
Require-Regex 'handle\(Player p[\s\S]*open:election-ceremony[\s\S]*openElectionCeremony' 'Ceremony GUI must be wired into click handler.'
Require-Regex 'handle\(Player p[\s\S]*election:expire-sealed-sessions[\s\S]*expireUndepositedBallotSessions' 'CIK must be able to safely expire sealed sessions that were never deposited.'
Require-Regex 'handle\(Player p[\s\S]*ceremony:debate[\s\S]*ceremony:stations-open[\s\S]*ceremony:final-call[\s\S]*ceremony:winner' 'Ceremony buttons must cover debate, station opening, final call, and winner moments.'

Require-Regex 'openVoting[\s\S]*approvedToCandidates\(actor\)[\s\S]*requireElectionGate\(eid,"VOTING",actor\)[\s\S]*transitionElectionStatus\(eid,ElectionStatus\.VOTING_OPEN' 'Opening voting must pass release gates after candidate sync and before enabling voting.'
Require-Regex 'startCounting[\s\S]*expireUndepositedBallotSessions\(eid,actor\)[\s\S]*requireElectionGate\(eid,"COUNTING",actor\)[\s\S]*syncCandidateVotesFromLedger[\s\S]*transitionElectionStatus\(eid,ElectionStatus\.COUNTING' 'Counting must expire non-deposited sealed sessions, gate the stage, then sync the vote ledger.'
Require-Regex 'finishElection[\s\S]*requireElectionGate\(eid,"FINISH",actor\)[\s\S]*syncCandidateVotesFromLedger[\s\S]*assignPresident' 'Finishing must pass release gates before assigning the president.'
Require-Regex 'releaseGateRows[\s\S]*cmv7_polling_stations[\s\S]*CIK_CHAIR[\s\S]*cmv7_ballot_issues[\s\S]*applications[\s\S]*duplicate voter[\s\S]*sealed_not_deposited' 'Release gates must check stations, chair, ballots, applications, duplicate votes, and sealed sessions.'
Require-Regex 'requireElectionGate[\s\S]*releaseGateRows[\s\S]*throw new SQLException' 'Release gate failures must block the transition with a clear error.'
Require-Regex 'expireUndepositedBallotSessions[\s\S]*SESSION_EXPIRED_NO_DEPOSIT[\s\S]*DELETE FROM cmv731_vote_sessions[\s\S]*ULTRA7_BALLOT_SEALED_EXPIRED' 'Sealed session cleanup must mark the ballot issue, delete the session, and audit the reason.'
Require-Regex 'announceStage[\s\S]*stageSubtitle[\s\S]*stageSound[\s\S]*broadcastElectionMoment' 'Stage announcements must use polished text, sound, and shared broadcast helper.'

Require-NotContains 'castInteractiveVote(' 'Direct GUI vote casting must be removed; votes should count only after physical ballot deposit.'
Require-NotContains 'ULTRA7_INTERACTIVE_VOTE' 'Audit trail must not contain direct interactive vote events bypassing station deposit.'

if ($errors.Count -gt 0) {
  throw ("Election release readiness validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election release readiness validation passed: gates, RP ceremony, sealed-session cleanup, and deposit-only vote counting are wired.'
