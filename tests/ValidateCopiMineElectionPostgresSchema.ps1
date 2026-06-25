$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$migration = Join-Path $root 'db\migrations\20260612_006_elections_hardening.sql'
$baseMigration = Join-Path $root 'db\migrations\20260611_001_copimine_v4_postgres.sql'
$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$sql = (Get-Content -Raw -Encoding UTF8 -LiteralPath $migration) + "`n" + (Get-Content -Raw -Encoding UTF8 -LiteralPath $baseMigration)
$errors = New-Object System.Collections.Generic.List[string]

function Require([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}
function RequireRx([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

foreach ($table in @(
  'elections',
  'applications',
  'candidates',
  'cmv7_ballot_issues',
  'cmv731_votes',
  'cmv731_vote_sessions',
  'cmv7_polling_stations',
  'cmv7_election_settings',
  'cmv7_election_curators',
  'cmv7_president_state',
  'cmv7_audit'
)) {
  Require $text $table "Plugin must create/use election table: $table"
}

foreach ($index in @(
  'ux_cmv731_votes_once',
  'ux_cmv731_votes_ballot_once',
  'ux_cmv7_candidates_once',
  'ux_cmv7_applications_active_once',
  'ux_cmv7_ballot_issues_active_once',
  'ux_cmv7_polling_stations_location_active'
)) {
  Require $sql $index "Hardening migration/index missing: $index"
  Require $text $index "Runtime ensureTables index missing: $index"
}

RequireRx $sql 'started_at BIGINT|started_at INTEGER' 'Election timestamps must be numeric.'
RequireRx $sql 'submitted_at BIGINT|submitted_at INTEGER' 'Application timestamps must be numeric.'
RequireRx $sql 'time BIGINT|time INTEGER' 'Vote/audit timestamps must be numeric.'

if ($errors.Count -gt 0) {
  throw ("Election PostgreSQL schema validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election PostgreSQL schema validation passed.'
