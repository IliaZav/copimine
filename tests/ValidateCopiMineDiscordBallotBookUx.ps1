$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginSource = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$botSource = Join-Path $root 'admin-web\backend\discord_bot.py'

$java = Get-Content -Raw -Encoding UTF8 $pluginSource
$bot = Get-Content -Raw -Encoding UTF8 $botSource
$errors = New-Object System.Collections.Generic.List[string]

function Require-Text([string]$name, [string]$text, [string]$needle) {
  if (-not $text.Contains($needle)) { $errors.Add("$name missing: $needle") }
}

function Require-Regex([string]$name, [string]$text, [string]$pattern) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add("$name missing pattern: $pattern")
  }
}

function Reject-Regex([string]$name, [string]$text, [string]$pattern) {
  if ([regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add("$name still contains forbidden pattern: $pattern")
  }
}

Require-Text "Discord digest marker" $bot "ELECTION_DIGEST_V2"
Require-Text "Discord election risk helper" $bot "def election_risk_lines("
Require-Text "Discord station stats" $bot "station_deposits"
Require-Text "Discord application issue stats" $bot "application_issues"
Require-Regex "Discord snapshot stations" $bot "polling_stations[\s\S]*active_stations[\s\S]*archived_stations"
Require-Regex "Discord snapshot ballots" $bot "ballots[\s\S]*unused_ballots[\s\S]*used_ballots"
Require-Text "Discord public showcase marker" $bot "PUBLIC_ELECTION_SHOWCASE_V3"
Require-Text "Discord public application filter" $bot "def is_public_application("
Require-Regex "Discord public embed fields" $bot "election_status_embed[\s\S]*top_candidates[\s\S]*candidate_application_summaries"

$statusStart = $bot.IndexOf("def election_status_embed")
$statusEnd = $bot.IndexOf("async def update_elections_status_channel", $statusStart)
if ($statusStart -ge 0 -and $statusEnd -gt $statusStart) {
  $statusBody = $bot.Substring($statusStart, $statusEnd - $statusStart)
  Reject-Regex "Discord public embed sensitive fields" $statusBody "voter_name|voter_uuid|ballot_id[\s\S]{0,200}candidate_name|station_deposits|unused_ballots|used_ballots|election_risk_lines"
}

Require-Text "Seal menu entry" $java 'openSealTargetMenu'
Require-Text "Application issue entry" $java 'issueApplicationBook'
Require-Text "Ballot issue entry" $java 'issueBallot'
Require-Regex "Seal revalidation" $java 'revalidateSealContext[\s\S]*expectedSealId[\s\S]*expectedStationId[\s\S]*expectedElectionId[\s\S]*expectedChairUuid'
Require-Regex "Chair recommendation only" $java 'chair:application:recommend:[\s\S]*chair:application:no-recommend:'

if ($errors.Count -gt 0) {
  Write-Host "Discord/ballot/book UX validation FAILED:" -ForegroundColor Red
  foreach ($e in $errors) { Write-Host " - $e" -ForegroundColor Red }
  exit 1
}

Write-Host "Discord/ballot/book UX validation passed: Discord digest remains public-safe and ElectionCore seal/application workflows are wired." -ForegroundColor Green
