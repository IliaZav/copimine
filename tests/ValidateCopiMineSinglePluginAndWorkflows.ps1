$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$plugins = Join-Path $root 'minecraft\server\plugins'
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'

$text = Get-Content -Raw -Encoding UTF8 $source
$pluginText = Get-Content -Raw -Encoding UTF8 $pluginYml

function Require-Contains([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) {
    throw $message
  }
}

function Require-Regex([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    throw $message
  }
}

$activeCopiMineJars = @(Get-ChildItem -LiteralPath $plugins -File -Filter 'CopiMine*.jar' | Select-Object -ExpandProperty Name | Sort-Object)
$expectedCopiMineJars = @(
  'CopiMineArtifacts.jar',
  'CopiMineEconomyCore.jar',
  'CopiMineElectionCore.jar',
  'CopiMineNarcotics.jar',
  'CopiMineUltimateAdminPlus.jar',
  'CopiMineWorldCore.jar'
)
if (($activeCopiMineJars -join '|') -ne ($expectedCopiMineJars -join '|')) {
  throw "All modular CopiMine jars must stay active. Active CopiMine jars: $($activeCopiMineJars -join ', ')"
}

$legacyCommands = @('cmeflow', 'cmpres', 'cmreports', 'cmstations', 'cmseal', 'cmballotadmin')
foreach ($command in $legacyCommands) {
  if ([regex]::IsMatch($pluginText, '(?m)^  ' + [regex]::Escape($command) + ':\s*$')) {
    throw "Legacy command /$command must not be exposed by AdminPlus after the election interface rebuild."
  }
}

foreach ($method in @(
  'openApplicationsReview',
  'openBallotsLedger',
  'openElectionAudit',
  'openPlayersTools',
  'openPlayerTimeline',
  'reopenPlayerContext',
  'snapshotAllOnline',
  'reviewApplication',
  'openApplications',
  'closeApplications',
  'openBallotIssue',
  'openVoting',
  'startCounting',
  'finishElection',
  'cancelElection',
  'prepareVoting',
  'prepareCounting'
)) {
  Require-Regex $text ('private .* ' + [regex]::Escape($method) + '\(') "Missing workflow method in AdminPlus: $method"
}

foreach ($action in @(
  'open:applications-review',
  'open:ballots-ledger',
  'open:election-audit',
  'open:players-tools',
  'open:p-timeline:',
  'players:snapshot-all',
  'players:heal-all',
  'players:feed-all',
  'players:unfreeze-all',
  'election:prepare-voting',
  'election:prepare-counting',
  'p:snapshot:',
  'p:arsync:'
)) {
  Require-Contains $text $action "Missing menu action wiring: $action"
}

Require-Regex $text 'if\(a\.startsWith\("p:"\)\)\{playerAction\(p,click,a\); reopenPlayerContext\(p,menuId,a\); return;\}' 'Player actions must reopen their working menu context instead of dumping the admin out.'
Require-Contains $text '"app-review:"+' 'Application review buttons must build approve/reject row actions.'
Require-Regex $text 'if\(a\.startsWith\("app-review:"\)\).*reviewApplication' 'Application review actions must approve/reject from the click handler.'
Require-Regex $text 'ballot-ledger' 'Ballot ledger menu must be present.'
Require-Regex $text 'election-audit' 'Election audit menu must be present.'

Write-Host 'CopiMine workflow validation passed: AdminPlus, Artifacts and Narcotics active, no legacy election commands, election/player workflow menus wired.'
