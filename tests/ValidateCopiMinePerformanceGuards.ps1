$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$backend = Join-Path $root "admin-web\backend\main.py"
$java = (Get-Content -Raw -Encoding UTF8 -LiteralPath $plugin) -replace "`r", ""
$py = (Get-Content -Raw -Encoding UTF8 -LiteralPath $backend) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

foreach ($needle in @(
  "copimine-postgres-worker",
  "openStartupReadiness",
  "runStartupSelfHeal",
  "performance_readiness_sync",
  "Chunky",
  "SeeMore",
  "startupSelfCheckRows"
)) {
  if (-not ($java + "`n" + $py).Contains($needle)) { $errors.Add("Performance guard missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("Performance guards validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Performance guards validation passed."
