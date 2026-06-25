$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$tabGroups = Join-Path $root "minecraft\server\plugins\TAB\groups.yml"

$errors = New-Object System.Collections.Generic.List[string]

function Read-Text([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) {
    $script:errors.Add("Missing file: $path")
    return ""
  }
  return (Get-Content -Raw -Encoding UTF8 -LiteralPath $path) -replace "`r", ""
}

function Require-Contains([string]$name, [string]$text, [string]$needle) {
  if (-not $text.Contains($needle)) { $script:errors.Add("$name is missing text: $needle") }
}

function Reject-Contains([string]$name, [string]$text, [string]$needle) {
  if ($text.Contains($needle)) { $script:errors.Add("$name still contains forbidden text: $needle") }
}

function Require-Regex([string]$name, [string]$text, [string]$pattern) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add("$name is missing pattern: $pattern")
  }
}

function Reject-Regex([string]$name, [string]$text, [string]$pattern) {
  if ([regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add("$name still matches forbidden pattern: $pattern")
  }
}

$java = Read-Text $plugin
$groups = Read-Text $tabGroups

Require-Contains "plugin source" $java "LIVE_SIDEBAR_ALWAYS_ON_V3"
Reject-Contains "sidebar tick" $java "if(activeElectionId()==null || !sidebarGlobal) return"
Reject-Contains "sidebar update" $java "if(eid==null){hideSidebar(p,false); return;}"
Reject-Contains "sidebar live results" $java 'if(onOff(st.get("show_live_results"))==0) return;'
Require-Contains "sidebar empty state" $java "sidebarNoElectionLines"
Require-Regex "sidebar uses latest election" $java "String eid=activeOrLatestElectionId\(\)"
Require-Regex "sidebar hidden votes" $java "boolean liveResults=.*show_live_results[\s\S]*sidebarLines\(eid,liveResults\)"
Require-Contains "sidebar main objective" $java 'SIDEBAR_OBJECTIVE = "cmulive"'
Require-Contains "sidebar global renderer" $java "updateGlobalSidebar"
Require-Regex "sidebar all uses main scoreboard" $java "updateGlobalSidebar[\s\S]*getMainScoreboard\(\)[\s\S]*renderSidebarObjective"
Require-Regex "sidebar show all clears hidden and closes menu" $java 'a\.equals\("sidebar:show"\)\)\{showSidebarAll\(true\)[\s\S]*p\.closeInventory\(\)'
Require-Regex "sidebar show me keeps personal panel and closes menu" $java 'a\.equals\("sidebar:show-me"\)\)\{sidebarHidden\.remove[\s\S]*sidebarPersonal\.add[\s\S]*p\.closeInventory\(\)'

Require-Contains "AR certification guard" $java "AR_CERTIFICATION_GATE_V3"
Require-Contains "AR certification guard" $java "isValidArCertificationBreak"
Require-Contains "AR certification guard" $java "Enchantment.SILK_TOUCH"
Require-Contains "AR certification guard" $java "GameMode.CREATIVE"
Require-Contains "AR certification guard" $java "GameMode.SPECTATOR"
Require-Contains "AR certification guard" $java "AR_CERTIFICATION_BLOCKED"
Require-Regex "AR count strict official only" $java "private int countArItem\(ItemStack it\)[\s\S]*return isOfficialAr\(it\)\?it\.getAmount\(\):0;"
Require-Contains "AR batch ids" $java "AR_UNIQUE_STACK_REGISTRY_V3"
Require-Contains "AR batch ids" $java "ensureArStackBatch"
Require-Contains "AR lore batch" $java "AR_STACK_BATCH_LABEL"
Require-Contains "AR lore batch" $java "AR_STACK_VISIBLE_ID"
Require-Contains "AR lore unique count" $java "AR_STACK_UNIQUE_COUNT"
Require-Contains "AR batch random" $java "UUID.randomUUID().toString()"

Require-Regex "adminhub three buttons only" $java "private void openHub\(Player p\)(?:(?!private void openAdminMap)[\s\S])*create\(m,27"
Reject-Regex "adminhub no extra shield" $java "private void openHub\(Player p\)(?:(?!private void openAdminMap)[\s\S])*Material\.SHIELD"

Require-Contains "application issue active/latest" $java "issuableElectionId"
Require-Regex "application book issue uses issuable election" $java "issueApplicationBook\(Player t,String actor\)[\s\S]*issuableElectionId\(\)"

Require-Contains "station clean GUI marker" $java "STATION_MINIMAL_GUI_V3"
Require-Regex "station is 27-slot" $java "openPollingStationHub\(Player p,Block block\)(?:(?!private void sendPollingStationCitizenInfo)[\s\S])*create\(m,27"
Reject-Regex "station no mandate recover" $java "openPollingStationHub\(Player p,Block block\)(?:(?!private void sendPollingStationCitizenInfo)[\s\S])*official:recover"
Reject-Regex "station no role docs" $java "openPollingStationHub\(Player p,Block block\)(?:(?!private void sendPollingStationCitizenInfo)[\s\S])*(president_mandate|cik_seal)"

Require-Contains "ballot clean GUI marker" $java "BALLOT_HEADS_ONLY_V3"
Require-Regex "ballot is 27-slot" $java "openBallotCandidateHub\(Player p, ItemStack ballot, String stationId\)(?:(?!private void openCandidateApplicationPreview)[\s\S])*create\(m,27"
Require-Contains "ballot right click application" $java "click.isRightClick()"
Require-Regex "ballot left confirm direct" $java "ballot-candidate:[\s\S]*openVoteConfirm\(p,parts\[1\],parts\[2\]"
Require-Regex "ballot right application direct" $java "ballot-candidate:[\s\S]*openCandidateApplicationPreview\(p,parts\[1\],parts\[2\]"
Require-Regex "ballot requires owned ballot from station" $java "ballot==null&&eid!=null&&!hasCitizenBallot\(p,eid\)"

Require-Contains "president mandate cooldown" $java "PRESIDENT_MANDATE_HOURLY_V3"
Require-Contains "president mandate cooldown" $java "presidentHourlyAnnouncement"
Require-Contains "president mandate cooldown" $java "3600000L"
Require-Regex "president action uses cooldown" $java 'president:announce[\s\S]{0,120}presidentHourlyAnnouncement'
Require-Regex "president item simplified" $java "createPresidentMandateItem[\s\S]*PRESIDENT_MANDATE_HOURLY_V3"

Require-Contains "role labels marker" $java "ROLE_LABELS_TAB_CHAT_NAMEPLATE_V3"
Require-Contains "role labels helper" $java "rolePrefix"
Require-Contains "role scoreboard helper" $java "applyRoleScoreboardTeam"
Require-Contains "chat format helper" $java "e.setFormat"
Require-Contains "TAB admin group" $groups "admin:"
Require-Contains "TAB president group" $groups "president:"
Require-Contains "TAB chair group" $groups "cik_chair:"

if ($errors.Count -gt 0) {
  throw ("Election/AR clean UX validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Election/AR clean UX validation passed."
