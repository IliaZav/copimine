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

foreach ($status in @('PENDING','APPROVED','REJECTED')) {
  Require $text $status "Application status missing in plugin: $status"
}
RequireRx $text 'onBook[\s\S]*requireElectionStatus\([^;]+APPLICATIONS_OPEN' 'Application submission must be allowed only during APPLICATIONS_OPEN.'
Require $text 'hasActiveApplication' 'Duplicate active application guard is missing.'
Require $sql 'ux_cmv7_applications_active_once' 'PostgreSQL active application uniqueness index is missing.'
RequireRx $text 'reviewApplication[\s\S]*ULTRA7_APPLICATION_' 'Application review must write audit rows.'
RequireRx $text 'promoteApplicationCandidate[\s\S]*SELECT COUNT\(\*\) FROM candidates WHERE election_id=\? AND uuid=\?' 'Application promotion must be idempotent.'
RequireRx $text 'promoteApplicationCandidate[\s\S]*INSERT INTO candidates' 'Approved application must be able to create a candidate.'
RequireRx $text 'reviewApplication[\s\S]*REJECTED' 'Rejected application status must exist and not promote by itself.'
Require $text 'openApplicationsReview' 'Applications review GUI is missing.'
Require $text 'verdict_reason' 'Application rejection/review reason field must be present.'

if ($errors.Count -gt 0) {
  throw ("Election applications validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election applications validation passed.'
