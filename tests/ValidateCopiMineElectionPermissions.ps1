$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$adminSource = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$adminPlugin = Join-Path $root "copimine-admin-plugin\plugin.yml"
$electionPlugin = Join-Path $root "copimine-election-core\plugin.yml"
$adminText = Get-Content -Raw -Encoding UTF8 -LiteralPath $adminSource
$adminYaml = Get-Content -Raw -Encoding UTF8 -LiteralPath $adminPlugin
$electionYaml = Get-Content -Raw -Encoding UTF8 -LiteralPath $electionPlugin
$errors = New-Object System.Collections.Generic.List[string]

function Require([string]$haystack, [string]$needle, [string]$message) {
  if (-not $haystack.Contains($needle)) { $script:errors.Add($message) }
}

function RequireRx([string]$haystack, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($haystack, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $script:errors.Add($message)
  }
}

foreach ($permission in @(
  "copimine.election.admin",
  "copimine.election.cik",
  "copimine.election.president",
  "copimine.election.debug"
)) {
  Require $electionYaml $permission "ElectionCore plugin.yml missing permission: $permission"
}

Require $electionYaml "hidelive:" "ElectionCore must expose the hidelive command."

foreach ($legacy in @(
  "copimine.elections.admin",
  "copimine.elections.curator"
)) {
  if ($adminYaml.Contains($legacy) -or $electionYaml.Contains($legacy)) {
    $errors.Add("Legacy permission alias must be removed: $legacy")
  }
}

RequireRx $adminText "hasElectionAdmin[\s\S]*copimine\.election\.admin[\s\S]*copimine\.election\.cik" "Election access gate must use the new election permission family."
Require $adminText "openElectionCoreHub" "Admin plugin must delegate elections to the new ElectionCore module."
Require $adminText "CopiMineElectionCore" "Legacy election entry should redirect to ElectionCore."

if ($errors.Count -gt 0) {
  throw ("Election permissions validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Election permissions validation passed."
