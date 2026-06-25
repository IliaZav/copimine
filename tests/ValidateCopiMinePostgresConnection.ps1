$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$backend = Join-Path $root "admin-web\backend\main.py"
$errors = New-Object System.Collections.Generic.List[string]

function Read-Text([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { $script:errors.Add("Missing file: $path"); return "" }
  return (Get-Content -Raw -Encoding UTF8 -LiteralPath $path) -replace "`r", ""
}

function Require-Contains([string]$name, [string]$text, [string]$needle) {
  if (-not $text.Contains($needle)) { $script:errors.Add("$name missing: $needle") }
}

$java = Read-Text $plugin
$py = Read-Text $backend

Require-Contains "plugin PostgreSQL driver" $java 'Class.forName("org.postgresql.Driver")'
Require-Contains "plugin PostgreSQL env file" $java "/opt/copimine/admin-web/.env"
Require-Contains "plugin PostgreSQL label" $java "postgresql://127.0.0.1:5432/copimine?schema=copimine"
Require-Contains "backend PG pool" $py "class SimplePgPool"
Require-Contains "backend PG ready" $py "def pg_ready() -> bool"
if (-not ($py.Contains("with auth_conn() as conn") -or ($py.Contains("def pg_connection(") -and $py.Contains("with pg_connection() as conn")))) {
  $errors.Add("backend pooled connection missing: auth_conn()/pg_connection() pooled usage")
}

if ($errors.Count -gt 0) {
  throw ("PostgreSQL connection validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "PostgreSQL connection validation passed."
