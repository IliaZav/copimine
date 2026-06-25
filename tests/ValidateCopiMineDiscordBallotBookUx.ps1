$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginSource = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'
$botSource = Join-Path $root 'admin-web\backend\discord_bot.py'
$bridgeSource = Join-Path $root 'admin-web\backend\minecraft_discord_bridge.py'

$java = Get-Content -Raw -Encoding UTF8 $pluginSource
$yml = Get-Content -Raw -Encoding UTF8 $pluginYml
$bot = Get-Content -Raw -Encoding UTF8 $botSource
$bridge = Get-Content -Raw -Encoding UTF8 $bridgeSource
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

Require-Text "plugin source version" $java "9.1.0-postgres-v4"
Require-Text "plugin.yml version" $yml "9.1.0-postgres-v4"

Require-Text "Discord digest marker" $bot "ELECTION_DIGEST_V2"
Require-Text "Discord election risk helper" $bot "def election_risk_lines("
Require-Text "Discord station stats" $bot "station_deposits"
Require-Text "Discord application issue stats" $bot "application_issues"
Require-Regex "Discord snapshot stations" $bot "cmv7_polling_stations[\s\S]*active_stations[\s\S]*archived_stations"
Require-Regex "Discord snapshot ballots" $bot "cmv7_ballot_issues[\s\S]*unused_ballots[\s\S]*used_ballots"
Require-Text "Discord public showcase marker" $bot "PUBLIC_ELECTION_SHOWCASE_V3"
Require-Text "Discord public application filter" $bot "def is_public_application("
Require-Regex "Discord public embed fields" $bot "election_status_embed[\s\S]*top_candidates[\s\S]*candidate_application_summaries"

$statusStart = $bot.IndexOf("def election_status_embed")
$statusEnd = $bot.IndexOf("async def update_elections_status_channel", $statusStart)
if ($statusStart -ge 0 -and $statusEnd -gt $statusStart) {
  $statusBody = $bot.Substring($statusStart, $statusEnd - $statusStart)
  Reject-Regex "Discord public embed sensitive fields" $statusBody "active_checks|ar_total|station_deposits|unused_ballots|used_ballots|application_issues|unsigned_application_issues|archived_stations|top_stations|election_risk_lines"
}

Require-Text "Bridge dedupe marker" $bridge "BRIDGE_DEDUPE_V2"
Require-Text "Bridge embed builder" $bridge "def build_bridge_embed_payload"
Require-Text "Bridge dedupe window" $bridge "DISCORD_BRIDGE_DEDUPE_SECONDS"
Require-Regex "Bridge sends structured payload" $bridge "send_discord[\s\S]*build_bridge_embed_payload"

Require-Text "Ballot GUI marker" $java "BALLOT_GUI_V2"
Require-Text "Station GUI marker" $java "STATION_GUI_V2"
Require-Regex "Ballot issue GUI stats" $java "openBallotsIssue[\s\S]*unusedBallots[\s\S]*usedBallots[\s\S]*stationDeposits"
Require-Regex "Ballot ledger deposits" $java "openBallotsLedger[\s\S]*station_id[\s\S]*issued_by[\s\S]*voter_name"
Require-Regex "Station card controls" $java "openPollingStationCard[\s\S]*open:give-ballot-player[\s\S]*open:ballots-ledger[\s\S]*open:citizen-guide"
Require-Regex "Station list controls" $java "openPollingStations[\s\S]*STATION_GUI_V2[\s\S]*open:give-ballot-player[\s\S]*open:ballots-ledger"

Require-Text "Application book marker" $java "APPLICATION_BOOK_V2_POLISH"
Require-Text "Application template helper" $java "private List<String> applicationBookTemplatePages"
Require-Regex "Application book uses template" $java "giveApplicationBook[\s\S]*applicationBookTemplatePages"
Require-Regex "Application book pages" $java "applicationBookTemplatePages[\s\S]*candidateGuidePage[\s\S]*campaignProgramPage[\s\S]*first24HoursPage[\s\S]*economyRulesPage[\s\S]*teamRisksPage[\s\S]*finalConsentPage"

if ($errors.Count -gt 0) {
  Write-Host "Discord/ballot/book UX validation FAILED:" -ForegroundColor Red
  foreach ($e in $errors) { Write-Host " - $e" -ForegroundColor Red }
  exit 1
}

Write-Host "Discord/ballot/book UX validation passed: Discord digest, station/ballot GUI, and application books are polished." -ForegroundColor Green
