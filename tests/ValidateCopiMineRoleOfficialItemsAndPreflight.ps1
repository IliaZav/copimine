$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$electionSource = Join-Path $root 'copimine-election-core\src\me\copimine\electioncore\CopiMineElectionCore.java'
$text = Get-Content -Raw -Encoding UTF8 $source
$election = Get-Content -Raw -Encoding UTF8 $electionSource
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

function Slice-Between([string]$text, [string]$startNeedle, [string]$endNeedle) {
  $start = $text.IndexOf($startNeedle)
  if ($start -lt 0) { return "" }
  $end = $text.IndexOf($endNeedle, $start + $startNeedle.Length)
  if ($end -lt 0) { return $text.Substring($start) }
  return $text.Substring($start, $end - $start)
}

Require-Contains 'cmv7_official_item_bindings' 'Official role items must have a persistent binding/recovery table.'
Require-Contains 'giveCikSealIfNeeded' 'CIK chair must be able to recover a personal seal from role/admin UI.'
Require-Contains 'givePresidentMandateIfNeeded' 'President must be able to recover a personal mandate from role/admin UI.'
Require-Contains 'hasActiveOfficialItemBinding' 'Recovery must not duplicate an already-bound official item.'
Require-Contains 'hasOwnedOfficialItem' 'Recovery must check whether the player already carries the official item.'
Require-Contains 'markOfficialItemDestroyed' 'Destroy-on-Q must revoke the active binding for recovery.'
Require-Contains 'restoreOfficialItem' 'Admin/role UI must expose safe official item recovery.'
Require-Contains 'official:recover:' 'Role/admin UI must route official item recovery through explicit actions.'

Require-Contains 'cik_seal' 'CIK seal item must use a stable PDC type.'
Require-Contains 'president_mandate' 'President mandate item must use a stable PDC type.'
Require-Regex 'handleDestroyableOfficialDrop[\s\S]*markOfficialItemDestroyed' 'Destroying a seal or mandate with Q must update the official item binding.'

$onInteract = Slice-Between $election 'public void onInteract(PlayerInteractEvent event)' '@EventHandler(priority = EventPriority.HIGHEST'
if ([string]::IsNullOrWhiteSpace($onInteract)) {
  $errors.Add('Could not isolate onInteract station click handler.')
} else {
  if ($onInteract.IndexOf('isRpStation') -lt 0 -or
      $onInteract.IndexOf('openRpBlocksMenu') -lt 0 -or
      $onInteract.IndexOf('openDirectVoteMenu') -lt 0) {
    $errors.Add('Station click handler must route RP blocks to admin management or direct voting.')
  }
  if ($onInteract.IndexOf('depositBallot') -ge 0 -or
      $onInteract.IndexOf('giveRoleOfficialItemsAtStation') -ge 0) {
    $errors.Add('The simplified RP block must not execute retired paper-ballot or role-item issuance branches.')
  }
}

Require-Contains 'openElectionPreflight' 'Chair/admin must have a preflight checklist screen before opening voting.'
Require-Contains 'preflightRows' 'Preflight checks must be generated in one shared helper.'
Require-Contains 'ULTRA7_PREFLIGHT' 'Preflight checks must be audit-visible.'
Require-Regex 'openChairPanel[\s\S]*open:preflight' 'Chair panel must expose the preflight checklist.'
Require-Regex 'openPollingStations[\s\S]*open:preflight' 'Polling station menu must expose the preflight checklist.'
Require-Contains 'PREFLIGHT_STATIONS' 'Preflight UI must cover polling stations.'
Require-Contains 'PREFLIGHT_CANDIDATES' 'Preflight UI must cover candidates.'
Require-Contains 'PREFLIGHT_BALLOTS' 'Preflight UI must cover ballots.'
Require-Contains 'PREFLIGHT_ITEM_GUARD' 'Preflight UI must cover official item protection.'

Require-Regex 'createCikSealItem[\s\S]*cik_seal[\s\S]*tagElectionItem' 'CIK seal must have polished lore and PDC binding.'
Require-Regex 'createPresidentMandateItem[\s\S]*president_mandate[\s\S]*tagElectionItem' 'President mandate must have polished lore and PDC binding.'
Require-Regex 'restoreOfficialItem[\s\S]*giveCikSealIfNeeded[\s\S]*givePresidentMandateIfNeeded[\s\S]*sound' 'Role recovery must provide visible RP feedback.'

if ($errors.Count -gt 0) {
  throw ("Role official items / preflight validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Role official item and preflight validation passed.'
