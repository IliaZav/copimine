$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$javaPath = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$botPath = Join-Path $root 'admin-web\backend\discord_bot.py'

$java = Get-Content -Raw -Encoding UTF8 $javaPath
$bot = Get-Content -Raw -Encoding UTF8 $botPath
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

function Slice-Between([string]$text, [string]$startNeedle, [string]$endNeedle) {
  $start = $text.IndexOf($startNeedle)
  if ($start -lt 0) { return "" }
  $end = $text.IndexOf($endNeedle, $start + $startNeedle.Length)
  if ($end -lt 0) { return $text.Substring($start) }
  return $text.Substring($start, $end - $start)
}

$publicStatus = Slice-Between $bot 'def election_status_embed' 'async def update_elections_status_channel'
if ([string]::IsNullOrWhiteSpace($publicStatus)) {
  $errors.Add('Could not isolate election_status_embed.')
} else {
  Require-Text 'Public election showcase marker' $publicStatus 'PUBLIC_ELECTION_SHOWCASE_V3'
  Require-Regex 'Public showcase candidates' $publicStatus 'top_candidates[\s\S]*candidate_application_summaries'
  Reject-Regex 'Public showcase sensitive counters' $publicStatus 'active_checks|ar_total|station_deposits|unused_ballots|used_ballots|application_issues|unsigned_application_issues|archived_stations|top_stations|election_risk_lines'
  Reject-Regex 'Public showcase vote totals' $publicStatus "row\.get\('total'|Голоса|votes|ballots"
}

Require-Text 'Public app state' $bot 'public_app_messages'
Require-Text 'Public app filter' $bot 'def is_public_application('
Require-Text 'Public app embed' $bot 'def public_application_embed('
Require-Text 'Public app sync' $bot 'async def sync_public_application_message'
Require-Text 'Public app delete' $bot 'async def delete_public_application_message'
Require-Regex 'Public app sends only through filter' $bot 'sync_public_application_message[\s\S]*is_public_application'
Require-Regex 'Public app embed is clean' $bot 'def public_application_embed[\s\S]*Кандидат[\s\S]*statement[\s\S]*set_footer'

$publicEmbed = Slice-Between $bot 'def public_application_embed' 'def report_embed'
if (-not [string]::IsNullOrWhiteSpace($publicEmbed)) {
  Reject-Regex 'Public application embed sensitive fields' $publicEmbed 'ID|election_id|reviewed_by|reviewed_at|verdict_reason|PENDING|DENIED|DELETED'
}

Require-Text 'Private station chat helper' $java 'private void sendPollingStationCitizenInfo'
$onInteract = Slice-Between $java 'public void onInteract(PlayerInteractEvent e)' '@EventHandler(priority=EventPriority.HIGHEST) public void onPlace'
if ([string]::IsNullOrWhiteSpace($onInteract)) {
    $errors.Add('Could not isolate onInteract station click handler.')
} else {
  Reject-Regex 'Legacy station click flow must stay disabled in AdminPlus onInteract' $onInteract 'depositSealedBallotAtStation|sendPollingStationCitizenInfo|openPollingStationHub|giveRoleOfficialItemsAtStation'
}

$stationInfo = Slice-Between $java 'private void sendPollingStationCitizenInfo' 'private void openCandidateDecision'
if (-not [string]::IsNullOrWhiteSpace($stationInfo)) {
  Require-Regex 'Station info is player-only chat' $stationInfo 'p\.sendMessage'
  Reject-Regex 'Station info must not open GUI' $stationInfo 'openInventory|create\(m'
  Reject-Regex 'Station info must not expose identifiers' $stationInfo 'shortId\(eid\)|station_id|cmv731_votes|ID:|Голосов'
}

$issueBallot = Slice-Between $java 'private void issueBallot(Player t,String actor)' 'private String annulApplicationEmergency'
if (-not [string]::IsNullOrWhiteSpace($issueBallot)) {
  Reject-Regex 'Issued ballot lore is player-safe' $issueBallot 'ID:|shortId\(id\)|Игрок:|Выборы:'
  Require-Regex 'Issued ballot lore explains only RP flow' $issueBallot 'Официальный бюллетень[\s\S]*Открой бюллетень[\s\S]*участок ЦИК[\s\S]*Чужой бюллетень не сработает'
}

$seal = Slice-Between $java 'private void sealBallotChoice(Player p,String eid,String candidateUuid,String stationId)' 'private void depositSealedBallotAtStation'
if (-not [string]::IsNullOrWhiteSpace($seal)) {
  Reject-Regex 'Sealed ballot lore keeps vote secret' $seal 'Выбор:'
  Require-Regex 'Sealed ballot lore explains deposit only' $seal 'Запечатанный бюллетень[\s\S]*Выбор записан внутри[\s\S]*опусти бюллетень в участок ЦИК'
}

$ballotHub = Slice-Between $java 'private void openBallotCandidateHub(Player p, ItemStack ballot, String stationId)' 'private void openCandidateApplicationPreview'
if (-not [string]::IsNullOrWhiteSpace($ballotHub)) {
  Reject-Regex 'Ballot GUI has no live totals' $ballotHub 'totalVotes|max=1|Голоса|Место|percent\(|graphBar\('
  Require-Text 'Ballot GUI clean marker' $ballotHub 'BALLOT_HEADS_ONLY_V3'
  Require-Regex 'Ballot GUI uses LMB vote and RMB application' $ballotHub 'ЛКМ[\s\S]*ПКМ[\s\S]*ballot-candidate:'
}

Require-Regex 'Application preview book is public-safe' $java 'applicationBookPages[\s\S]*Официальная заявка ЦИК[\s\S]*Программа кандидата'
Reject-Regex 'Application preview book hides internal status' $java 'Статус: "\+status|shortId\(eid\)\+"\\nСтатус'

if ($errors.Count -gt 0) {
  Write-Host "Public privacy UX validation FAILED:" -ForegroundColor Red
  foreach ($e in $errors) { Write-Host " - $e" -ForegroundColor Red }
  exit 1
}

Write-Host "Public privacy UX validation passed: Discord, station clicks, ballots, and application books are player-safe." -ForegroundColor Green
