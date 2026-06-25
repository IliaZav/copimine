$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$migration = Join-Path $root 'db\migrations\20260612_006_elections_hardening.sql'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $migration
$errors = New-Object System.Collections.Generic.List[string]

function Require([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}
function RequireRx([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require $text 'tagElectionItem(meta,"ballot"' 'Ballot item must be tagged with PDC metadata.'
Require $text 'isBallotItem' 'Ballot validation helper is missing.'
Require $text 'requireElectionItemOwner' 'Ballot owner validation is missing.'
Require $text 'findOwnedUnusedBallot' 'Owned unused ballot lookup is missing.'
RequireRx $text 'depositSealedBallotAtStation[\s\S]*id=\? AND election_id=\? AND voter_uuid=\? AND COALESCE\(used,0\)=0' 'Deposit must verify ballot id, election, owner and unused state.'
Require $text 'removeBallotById' 'Used ballot must be removed from inventory after successful deposit.'
Require $sql 'ux_cmv7_ballot_issues_active_once' 'Active ballot uniqueness index is missing.'
Require $text 'cmv7_ballot_issues' 'Ballot issue ledger table is missing.'

if ($errors.Count -gt 0) {
  throw ("Election ballots validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election ballots validation passed.'
