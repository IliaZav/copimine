$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$backend = Join-Path $root "admin-web\backend\main.py"
$frontend = Join-Path $root "admin-web\frontend\assets\app.js"
$errors = New-Object System.Collections.Generic.List[string]

function Require-Text([string]$name, [string]$text, [string]$pattern) {
  if ($text -notmatch $pattern) { $errors.Add("${name} missing: $pattern") }
}

function Require-NotText([string]$name, [string]$text, [string]$pattern) {
  if ($text -match $pattern) { $errors.Add("${name} still contains retired flow: $pattern") }
}

$java = Get-Content -LiteralPath $plugin -Raw -Encoding UTF8
$py = Get-Content -LiteralPath $backend -Raw -Encoding UTF8
$js = Get-Content -LiteralPath $frontend -Raw -Encoding UTF8

Require-Text "plugin redirect" $java "CopiMineElectionCore"
Require-Text "plugin hidelive hint" $java "hidelive"
Require-Text "backend gui-only status code" $py "status_code=410"
Require-Text "backend gui-only election core message" $py "CopiMineElectionCore"
Require-Text "backend gui-only emergency message" $py "GUI"

foreach ($legacy in @(
  "cmultra issueapp",
  "cmultra issueballot",
  "cmultra annulapp",
  "cmultra annulballot"
)) {
  Require-NotText "backend legacy command" $py ([regex]::Escape($legacy))
}

foreach ($marker in @(
  "issueElectionApplication",
  "issueElectionBallot",
  "annulElectionApplication",
  "annulElectionBallot",
  "electionControl"
)) {
  if ($js -match [regex]::Escape($marker)) {
    $errors.Add("Frontend still exposes retired election emergency entry: $marker")
  }
}

if ($errors.Count -gt 0) {
  throw ("Election emergency admin tools validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Election emergency admin tools validation passed."
