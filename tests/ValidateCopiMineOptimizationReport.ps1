$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$workspace = Split-Path -Parent (Split-Path -Parent $root)
$report = Join-Path $workspace "docs\COPIMINE_OPTIMIZATION_REPORT_2026-06-11.md"
$text = (Get-Content -Raw -Encoding UTF8 -LiteralPath $report) -replace "`r", ""
$errors = New-Object System.Collections.Generic.List[string]

foreach ($needle in @(
  "SimplePgPool",
  "PostgreSQL V4",
  "copimine-admin.service",
  "copimine-discord-bot.service",
  "Chunky",
  "SeeMore",
  "live Ubuntu smoke"
)) {
  if (-not $text.Contains($needle)) { $errors.Add("Optimization report missing: $needle") }
}

if ($errors.Count -gt 0) {
  throw ("Optimization report validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Optimization report validation passed."
