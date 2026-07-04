$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$scriptPath = Join-Path $root "deploy\ubuntu\verify.sh"
$readme = Join-Path $root "admin-web\README_RU.md"
$guide = Join-Path (Split-Path -Parent (Split-Path -Parent $root)) "COPIMINE_TRANSFER_GUIDE.txt"
$errors = New-Object System.Collections.Generic.List[string]

foreach ($path in @($scriptPath, $readme, $guide)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing file: $path") }
}

$script = (Get-Content -Raw -Encoding UTF8 -LiteralPath $scriptPath) -replace "`r", ""
foreach ($needle in @(
  "systemctl is-active --quiet copimine-admin",
  "curl -fsS",
  "/api/health",
  "/api/runtime",
  "copimine_verify_runtime"
)) {
  if (-not $script.Contains($needle)) { $errors.Add("Live smoke script missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("Ubuntu live smoke validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Ubuntu live smoke validation passed."
