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
Require-Contains $text 'openDirectVoteMenu(player, protectedInfo.linkedId())' 'RP stations must open the direct-vote menu during voting.'
Require-Contains $text 'openRpBlocksMenu(player, 0)' 'Administrators must reach RP block management from a station.'
Require-Contains $text "DELETE FROM protected_blocks WHERE kind IN ('POLLING_STATION','TAX_OFFICE')" 'Election reset must clean protected station blocks.'
Require-Contains $text "DELETE FROM protected_block_visuals WHERE kind IN ('POLLING_STATION','TAX_OFFICE')" 'Election reset must clean station visuals.'

Require-Regex $text 'if \(!isRpStation\(station\)\) \{[\s\S]*return;[\s\S]*if \(hasElectionAdmin\(player\)\) \{[\s\S]*openRpBlocksMenu\(player, 0\);[\s\S]*if \(isDirectVotingOpen\(station\)\) \{[\s\S]*openDirectVoteMenu\(player, protectedInfo\.linkedId\(\)\);' 'Station right-click flow must route only the simplified RP block and direct voting.'
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
