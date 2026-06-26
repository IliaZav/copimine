$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$migration = Join-Path $root 'db\migrations\20260621_007_copimine_election_core_rebuild.sql'

$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $migration
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains $text 'createPollingStationFromTarget' 'Polling station creation helper is missing.'
Require-Contains $text 'removePollingStation' 'Polling station removal helper is missing.'
Require-Contains $text 'openChairStationMenu' 'Chair station GUI is missing.'
Require-Contains $text 'openSealTargetMenu' 'Seal issue GUI is missing.'
Require-Contains $text 'protectedBlockInfo(clicked)' 'Station interaction must resolve protected blocks.'
Require-Contains $text 'submitApplicationBook(player, event.getItem(), protectedInfo.linkedId())' 'Application books must submit through the station block.'
Require-Contains $text 'depositBallot(player, event.getItem(), protectedInfo.linkedId())' 'Confirmed ballots must be deposited through the station block.'
Require-Contains $text 'UPDATE polling_stations SET ballots_submitted=ballots_submitted+1' 'Station deposit flow must increment submitted ballots.'
Require-Contains $text 'UPDATE polling_stations SET ballots_annulled=ballots_annulled+1' 'Station annul flow must increment annulled ballots.'
Require-Contains $text "DELETE FROM protected_blocks WHERE kind IN ('POLLING_STATION','TAX_OFFICE')" 'Election reset must clean protected station blocks.'
Require-Contains $text "DELETE FROM protected_block_visuals WHERE kind IN ('POLLING_STATION','TAX_OFFICE')" 'Election reset must clean station visuals.'

Require-Regex $text 'if \(!"POLLING_STATION"\.equals\(protectedInfo\.kind\(\)\)\) \{[\s\S]*return;[\s\S]*if \(isApplicationBook\(event\.getItem\(\)\)\) \{[\s\S]*submitApplicationBook\(player, event\.getItem\(\), protectedInfo\.linkedId\(\)\);[\s\S]*if \(isBallot\(event\.getItem\(\)\) && isConfirmedBallot\(event\.getItem\(\)\)\) \{[\s\S]*depositBallot\(player, event\.getItem\(\), protectedInfo\.linkedId\(\)\);[\s\S]*if \(isChairForStation\(player, station\)\) \{[\s\S]*openChairStationMenu\(player, protectedInfo\.linkedId\(\), 0\);' 'Station right-click flow must handle submit, deposit, and chair access on the protected block.'
Require-Regex $text 'private void openChairStationMenu\(Player player, String stationId, int page\) throws Exception \{[\s\S]*"chair:applications:" \+ stationId[\s\S]*"chair:ballots:" \+ stationId' 'Chair station GUI must link to applications and ballots.'
Require-Regex $text 'private void createPollingStationFromTarget\(Player player\) throws Exception \{[\s\S]*INSERT INTO polling_stations[\s\S]*protected_blocks[\s\S]*spawnOrReplaceProtectedBlockVisual' 'Station creation must persist polling station rows, protected blocks, and visuals together.'
Require-Regex $text 'private void removePollingStation\(Player player, String stationId\) throws Exception \{[\s\S]*cleanupNearbyTextDisplays\(stationId\);[\s\S]*cleanupProtectedBlockVisuals\("POLLING_STATION", stationId\);[\s\S]*UPDATE polling_stations SET active=0[\s\S]*UPDATE protected_blocks SET active=0' 'Station removal must archive row, protected block, text display, and visual overlay together.'

Require-Contains $sql 'CREATE TABLE IF NOT EXISTS polling_stations' 'Election migration must create polling_stations.'
Require-Contains $sql 'CREATE TABLE IF NOT EXISTS protected_blocks' 'Election migration must create protected_blocks.'
Require-Contains $sql 'CREATE INDEX IF NOT EXISTS idx_polling_stations_active' 'Election migration must index active stations.'
Require-Contains $sql 'CREATE INDEX IF NOT EXISTS idx_protected_blocks_coords' 'Election migration must index protected block coordinates.'

if ($errors.Count -gt 0) {
  throw ("Election stations validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election stations validation passed: protected station blocks, chair GUI, book submission, ballot deposit, and cleanup are wired through ElectionCore.'
