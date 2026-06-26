$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$adminPlus = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'

$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$adminText = Get-Content -Raw -Encoding UTF8 -LiteralPath $adminPlus
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

function Reject-Contains([string]$haystack, [string]$needle, [string]$message) {
  if ($haystack.Contains($needle)) { $script:errors.Add($message) }
}

Require-Contains $text 'ElectionStateMachine' 'Missing ElectionStateMachine for guarded stage transitions.'
Require-Contains $text 'validateStageTransition(connection, electionId, from, stage)' 'setStage must validate transitions through the state machine.'
Require-Contains $text 'setStage(requireActiveElectionId(), ElectionStage.valueOf(raw), player.getName(),' 'GUI stage actions must route through setStage.'
Require-Contains $text 'countCurrentRoundStrict(actor);' 'Counting flow must use strict round counting.'
Require-Contains $text 'startSecondRoundStrict(actor);' 'Second-round flow must use strict round preparation.'
Require-Contains $text 'chooseWinner(action.substring("apply:results:winner:".length()), player.getName());' 'Winner confirmation must stay behind explicit GUI confirmation.'
Require-Contains $text 'resetElections(player.getName());' 'Reset flow must remain explicit and actor-bound.'
Require-Contains $text 'confirm:apply' 'Risky actions must go through shared confirmation UI.'
Require-Contains $text 'confirm:back' 'Confirmation UI must provide a back path.'

Require-Regex $text 'if \(action\.startsWith\("apply:stage:"\)\)[\s\S]*setStage\(requireActiveElectionId\(\), ElectionStage\.valueOf\(raw\), player\.getName\(\),' 'apply:stage:* must call setStage rather than direct SQL.'
Require-Regex $text 'private void setStage\(String electionId, ElectionStage stage, String actor, String notes\) throws Exception \{[\s\S]*validateStageTransition\(connection, electionId, from, stage\)' 'setStage must validate transitions inside the DB transaction.'
Require-Regex $text 'private void countCurrentRoundStrict\(String actor\) throws Exception \{[\s\S]*ElectionContext context = requireActiveElectionContext\(connection\);[\s\S]*context\.stage\(\) != ElectionStage\.VOTING' 'Counting must require an active VOTING-stage election context.'
Require-Regex $text 'private void startSecondRoundStrict\(String actor\) throws Exception \{[\s\S]*validateStageTransition\(connection, electionId, from, ElectionStage\.SECOND_ROUND\)' 'Second round must be blocked by the state machine.'
Require-Regex $text 'private void chooseWinner\(String candidateUuid, String actor\) throws Exception \{[\s\S]*UPDATE rounds SET status=''COUNTED''' 'Winner confirmation must persist counted-round state before mandate assignment.'
Require-Regex $text 'private void resetElections\(String actor\) throws Exception \{[\s\S]*"votes"[\s\S]*"ballots"[\s\S]*"polling_stations"[\s\S]*"elections"[\s\S]*DELETE FROM protected_blocks WHERE kind IN \(''POLLING_STATION'',''TAX_OFFICE''\)[\s\S]*DELETE FROM text_display_links WHERE kind IN \(''STATION_LABEL'',''TAX_LABEL''\)[\s\S]*DELETE FROM protected_block_visuals WHERE kind IN \(''POLLING_STATION'',''TAX_OFFICE''\)' 'Reset must clear only election-owned runtime tables.'
Require-Regex $adminText 'openAdminElectionHub' 'AdminPlus must delegate election admin entry to ElectionCore.'

Reject-Contains $text 'castInteractiveVote(' 'Interactive vote casting must stay removed; counting is deposit-only.'
Reject-Contains $text 'ULTRA7_INTERACTIVE_VOTE' 'Legacy interactive vote audit marker must stay removed.'

if ($errors.Count -gt 0) {
  throw ("Election release readiness validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election release readiness validation passed: GUI stage changes, strict counting, manual winner confirmation, and reset flows are aligned with ElectionCore.'
