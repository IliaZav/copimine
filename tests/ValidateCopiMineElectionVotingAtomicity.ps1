$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$migration = Join-Path $root 'db\migrations\20260612_006_elections_hardening.sql'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $migration
$errors = New-Object System.Collections.Generic.List[string]

function RequireRx([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}
function Require([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}

RequireRx $text 'depositSealedBallotAtStation[\s\S]*tx\(c -> \{[\s\S]*INSERT INTO cmv731_votes[\s\S]*UPDATE candidates[\s\S]*INSERT INTO cmv7_audit' 'Vote deposit, ballot mark, candidate count and audit must run in one transaction.'
RequireRx $text 'depositSealedBallotAtStation[\s\S]*SELECT COUNT\(\*\) FROM cmv731_votes WHERE election_id=\? AND voter_uuid=\?' 'Vote deposit must check duplicate voter before insert.'
RequireRx $text 'depositSealedBallotAtStation[\s\S]*UPDATE cmv7_ballot_issues SET used=1[\s\S]*COALESCE\(used,0\)=0' 'Ballot must be marked used atomically with a used=0 condition.'
Require $text 'ELECTION_VOTE_DUPLICATE' 'Duplicate vote error code is missing.'
Require $text 'ELECTION_BALLOT_ALREADY_USED' 'Already-used ballot error code is missing.'
Require $sql 'ux_cmv731_votes_once' 'Unique voter/election vote constraint is missing.'
Require $sql 'ux_cmv731_votes_ballot_once' 'Unique ballot/election vote constraint is missing.'
Require $text 'PRIMARY KEY(voter_uuid,election_id)' 'Vote session double-click guard must have a primary key.'

if ($errors.Count -gt 0) {
  throw ("Election voting atomicity validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election voting atomicity validation passed.'
