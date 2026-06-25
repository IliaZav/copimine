$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$source = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$text = (Get-Content -Raw -Encoding UTF8 -LiteralPath $source) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $script:errors.Add($message) }
}

function Reject-Contains([string]$needle, [string]$message) {
  if ($text.Contains($needle)) { $script:errors.Add($message) }
}

function Require-Regex([string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

$chair = [regex]::Match($text, "private void openChairPanel\(Player p\) throws Exception\{(?<body>[\s\S]*?)\n    private void openApplicationsIssue", [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $chair.Success) {
  $errors.Add("openChairPanel block was not found.")
} else {
  $chairBody = $chair.Groups["body"].Value
  if (-not [regex]::IsMatch($chairBody, 'create\(m,27,"&b&l')) { $errors.Add("CIK seal/chair panel must be a compact 27-slot info menu.") }
  foreach ($needle in @(
    "CIK_SEAL_PLAYER_ONLY_V5",
    "Material.PLAYER_HEAD",
    "Material.PAPER",
    "Material.LECTERN"
  )) {
    if (-not $chairBody.Contains($needle)) { $errors.Add("Player-only CIK panel is missing $needle.") }
  }
  foreach ($forbidden in @(
    "chair:open-voting",
    "chair:close-voting",
    "open:applications-issue",
    "open:ballots-issue",
    "open:polling-stations",
    "sidebar:show-me",
    "open:election-recovery-advanced",
    "open:election-release",
    "open:election-ceremony",
    "open:curators",
    "open:election-settings",
    "open:election-integrity",
    "open:election-emergency",
    "chair:station-kit",
    "chair:issue-applications",
    "chair:issue-ballots"
  )) {
    if ($chairBody.Contains($forbidden)) { $errors.Add("Compact CIK panel still exposes overloaded action $forbidden.") }
  }
}

Require-Contains "SIDEBAR_RELIABLE_MAIN_SCOREBOARD_V4" "Live sidebar must carry the reliable-main-scoreboard marker."
Require-Regex 'private void showSidebarAll\(boolean sound\)\{ sidebarGlobal=true; sidebarHidden\.clear\(\); sidebarPersonal\.clear\(\); try\{updateGlobalSidebar\(true\);' "Show-all must force the global main scoreboard and clear hidden/personal state."
Require-Regex 'a\.equals\("sidebar:show"\)\)\{showSidebarAll\(true\)[\s\S]*p\.closeInventory\(\)' "Show-all button must close GUI after drawing the panel."
Require-Regex 'a\.equals\("sidebar:show-me"\)\)\{sidebarHidden\.remove[\s\S]*sidebarPersonal\.add[\s\S]*updateSidebar\(p,true\)[\s\S]*p\.closeInventory\(\)' "Show-me button must install a persistent personal panel and close GUI."

if ($errors.Count -gt 0) {
  throw ("Live sidebar / compact CIK validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Live sidebar and compact CIK panel validation passed."
