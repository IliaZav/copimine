$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$source = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$text = (Get-Content -Raw -Encoding UTF8 -LiteralPath $source) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

function Reject-Regex([string]$pattern, [string]$message) {
  if ([regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

Require-Contains "CIK_SEAL_PLAYER_ONLY_V5" "CIK seal must be marked as player-only workflow."
Require-Contains "openCikSealPlayerPanel" "CIK seal must open a targeted player status/issue panel."
$entityHandler = [regex]::Match($text, "public void onProtectedEntityDisplay\(PlayerInteractEntityEvent e\)\{(?<body>[\s\S]*?)\n    \}", [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $entityHandler.Success) {
  $errors.Add("onProtectedEntityDisplay block was not found.")
} else {
  $body = $entityHandler.Groups["body"].Value
  foreach ($needle in @("officialTypeForStack(hand)", "`"cik_seal`"", "target instanceof Player t", "openCikSealPlayerPanel")) {
    if (-not $body.Contains($needle)) { $errors.Add("Right-clicking a player with the CIK seal must open the target panel.") ; break }
  }
}
$interact = [regex]::Match($text, "public void onInteract\(PlayerInteractEvent e\)\{(?<body>[\s\S]*?)\n    \@EventHandler", [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $interact.Success) {
  $errors.Add("onInteract block was not found.")
} else {
  $body = $interact.Groups["body"].Value
  if (-not $body.Contains("cik_seal")) {
    $errors.Add("Right-clicking air/block with the CIK seal must be handled before generic election UI.")
  }
  if ($body.Contains("openChairPanel")) {
    $errors.Add("CIK seal must no longer open the overloaded chair panel from item right-click.")
  }
}

Require-Contains "targetNeedsApplication" "CIK target panel must guard duplicate application issue."
Require-Contains "targetNeedsBallot" "CIK target panel must guard duplicate ballot issue."
Require-Regex "cik-target-app:[\s\S]*targetNeedsApplication[\s\S]*issueApplicationBook" "Application issue action must only issue when the player has no active application/book."
Require-Regex "cik-target-ballot:[\s\S]*targetNeedsBallot[\s\S]*issueBallot" "Ballot issue action must only issue when the player has no active unused ballot/vote."

$chair = [regex]::Match($text, "private void openChairPanel\(Player p\) throws Exception\{(?<body>[\s\S]*?)\n    private void openApplicationsIssue", [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $chair.Success) {
  $errors.Add("openChairPanel block was not found.")
} else {
  $body = $chair.Groups["body"].Value
  foreach ($forbidden in @(
    "chair:open-voting",
    "chair:close-voting",
    "open:lifecycle",
    "open:preflight",
    "open:election-audit",
    "open:election-recovery-advanced",
    "open:applications-issue",
    "open:ballots-issue",
    "open:polling-stations"
  )) {
    if ($body.Contains($forbidden)) { $errors.Add("Chair/seal panel still exposes forbidden action $forbidden.") }
  }
}

Require-Contains "STATION_RIGHT_CLICK_DEPOSIT_ONLY_V5" "Polling station GUI must explain right-click deposit with the physical sealed ballot."
Require-Regex "isPollingStationBlock\(e\.getClickedBlock\(\)\)[\s\S]*isBallotItem\(e\.getItem\(\)\)&&isSealedBallot\(e\.getItem\(\)\)[\s\S]*depositSealedBallotAtStation\(p,e\.getItem\(\),stationId" "Right-clicking a station with a sealed ballot in hand must directly deposit the vote."
$stationHub = [regex]::Match($text, "private void openPollingStationHub\(Player p,Block block\)throws Exception\{(?<body>[\s\S]*?)\n    private void sendPollingStationCitizenInfo", [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $stationHub.Success) {
  $errors.Add("openPollingStationHub block was not found.")
} elseif ($stationHub.Groups["body"].Value.Contains("vote-deposit:")) {
  $errors.Add("Polling station GUI must not provide a GUI deposit shortcut; deposit is physical right-click only.")
}

if ($errors.Count -gt 0) {
  throw ("CIK seal player issue/deposit validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "CIK seal player issue/deposit validation passed."
