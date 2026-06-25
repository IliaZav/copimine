$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'
$plugins = Join-Path $root 'minecraft\server\plugins'

$text = Get-Content -Raw -Encoding UTF8 $source
$pluginText = Get-Content -Raw -Encoding UTF8 $pluginYml
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) {
    $script:errors.Add($message)
  }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains 'openPresidentPanel' 'President panel is missing.'
Require-Contains 'openChairPanel' 'CIK chair panel is missing.'
Require-Contains 'president:announce' 'President cannot publish an in-game address.'
Require-Contains 'president:program' 'President cannot publish/update a program action.'
Require-Contains 'president:appoint-delegate' 'President cannot delegate an election representative.'
Require-Contains 'president:request-counting' 'President cannot formally request counting.'
Require-Contains 'chair:issue-ballots' 'CIK chair cannot issue ballots from a dedicated panel.'
Require-Contains 'chair:issue-applications' 'CIK chair cannot issue applications from a dedicated panel.'
Require-Contains 'chair:review' 'CIK chair cannot review applications from a dedicated panel.'
Require-Contains 'chair:open-voting' 'CIK chair cannot open the voting stage.'
Require-Contains 'chair:close-voting' 'CIK chair cannot close voting for counting.'

Require-Contains 'openBallotCandidateHub' 'Ballot right-click does not open a candidate/application hub.'
Require-Contains 'openCandidateApplicationPreview' 'Candidate application preview action is missing.'
Require-Contains 'giveTemporaryApplicationBook' 'Temporary signed application preview book is missing.'
Require-Contains 'purgeTemporaryApplicationBooks' 'Temporary application preview books are not purged.'
Require-Contains 'temporary_application_book' 'Temporary application preview book is not tagged.'
Require-Contains 'WRITTEN_BOOK' 'Candidate application preview must use a signed book.'
Require-Contains 'openBook(book)' 'Candidate application preview book should open automatically.'
Require-Regex 'onInteract[\s\S]*isTemporaryApplicationBook[\s\S]*return[\s\S]*isBallotItem[\s\S]*openBallotCandidateHub[\s\S]*isElectionUiItem[\s\S]*openCitizenElectionHub' 'Interact handler must allow temp books, route ballots to candidate applications, then fall back to citizen hub.'
Require-Regex 'onInventoryClose[\s\S]*purgeTemporaryApplicationBooks' 'Temporary application books must disappear when the player closes an inventory/book UI.'
Require-Regex 'onDrop[\s\S]*isTemporaryApplicationBook[\s\S]*remove' 'Temporary application books must not be droppable or left in the world.'
Require-Regex 'onBook[\s\S]*!"application_book"\.equals\(electionItemString\(old,\s*"type"\)\)' 'Official application books must be accepted on signing, not ignored.'
if ($text.Contains('if("application_book".equals(electionItemString(old,"type"))) return;')) {
  $errors.Add('Official application books are still skipped before submission.')
}

foreach ($action in @('nearby','panic','randomtp','confuse','inventory-lock','paperwork','tinyquest','sneeze','snowcloud','knockback','fakecredits')) {
  if (-not ($text.Contains('p:' + $action + ':') -or [regex]::IsMatch($text, '"' + [regex]::Escape($action) + '"\s*\}'))) {
    $errors.Add("Missing expanded player/prank action: p:${action}:")
  }
  Require-Regex ('case\s+"' + [regex]::Escape($action) + '"\s*->') "Missing handler for expanded player/prank action: $action"
}

foreach ($command in @('cmpres','cmeflow','cmballotadmin')) {
  if ([regex]::IsMatch($pluginText, "(?m)^  $command\s*:")) {
    $errors.Add("plugin.yml must not expose removed legacy election command /$command.")
  }
}

$activeCopiMineJars = @(Get-ChildItem -LiteralPath $plugins -File -Filter 'CopiMine*.jar' | Select-Object -ExpandProperty Name | Sort-Object)
$expectedCopiMineJars = @('CopiMineArtifacts.jar', 'CopiMineElectionCore.jar', 'CopiMineNarcotics.jar', 'CopiMineUltimateAdminPlus.jar')
if (($activeCopiMineJars -join '|') -ne ($expectedCopiMineJars -join '|')) {
  $errors.Add("Exactly CopiMineArtifacts.jar, CopiMineElectionCore.jar, CopiMineNarcotics.jar and CopiMineUltimateAdminPlus.jar must stay active. Active CopiMine jars: $($activeCopiMineJars -join ', ')")
}

if ($errors.Count -gt 0) {
  throw ("Election role/candidate-book validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Election role/candidate-book validation passed: president/chair panels, ballot application preview books, and expanded player/prank controls are wired.'
