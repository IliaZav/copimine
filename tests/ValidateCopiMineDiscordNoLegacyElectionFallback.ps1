. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Discord

Require-Contains $text 'ALLOW_LEGACY_ELECTION_FALLBACK' 'Discord bot must gate legacy election fallback behind an env flag.'
Require-Contains $text 'def election_overview_snapshot() -> dict[str, Any]:' 'Discord bot must expose the election overview wrapper.'
Require-Contains $text 'return election_overview_snapshot_v2()' 'Discord overview must use ElectionCore V2 snapshot by default.'
Require-NotContains $text 'return election_overview_snapshot() if ALLOW_LEGACY_ELECTION_FALLBACK else {' 'Discord must not advertise a live legacy overview fallback.'
Require-NotContains $text 'return candidate_application_summaries(election_id, top_candidates, limit)' 'Candidate application summaries must not fall back to legacy data.'
Require-Contains $text 'for row in (top_candidates or [])[:limit]' 'Candidate application fallback must degrade to current top candidates only.'

Throw-IfErrors 'ValidateCopiMineDiscordNoLegacyElectionFallback'
