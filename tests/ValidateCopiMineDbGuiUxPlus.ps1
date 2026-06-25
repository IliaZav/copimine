$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginSource = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$backendSource = Join-Path $root 'admin-web\backend\main.py'
$frontendSource = Join-Path $root 'admin-web\frontend\assets\app.js'
$styleSource = Join-Path $root 'admin-web\frontend\assets\style.css'

$plugin = Get-Content -Raw -Encoding UTF8 $pluginSource
$backend = Get-Content -Raw -Encoding UTF8 $backendSource
$frontend = Get-Content -Raw -Encoding UTF8 $frontendSource
$style = Get-Content -Raw -Encoding UTF8 $styleSource

$errors = New-Object System.Collections.Generic.List[string]

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) { $errors.Add($message) }
}

function Require-Regex([string]$text, [string]$pattern, [string]$message) {
  if (-not [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    $errors.Add($message)
  }
}

Require-Contains $backend 'DB_WRITE_PROTECTED_TABLE_PATTERNS' 'Backend must define protected table patterns for raw DB writes.'
Require-Contains $backend 'def db_write_policy(' 'Backend must expose a structured DB write policy helper.'
Require-Contains $backend 'def open_sqlite_write(' 'Backend must use a write connection helper with SQLite PRAGMAs.'
Require-Contains $backend '"writePolicy"' 'DB table schema/config must return writePolicy details for the frontend.'
Require-Regex $backend 'db_write_policy[\s\S]*cmv731_votes[\s\S]*cmv7_ar_[\s\S]*cmv7_audit' 'DB policy must protect election votes, AR ledgers and audit tables from generic writes.'
Require-Regex $backend 'open_sqlite_write[\s\S]*busy_timeout[\s\S]*journal_mode=WAL[\s\S]*foreign_keys=ON' 'SQLite writes must set busy_timeout, WAL and foreign key PRAGMAs.'

Require-Contains $frontend 'function dbPolicyPanel(' 'Frontend must render a dedicated DB policy panel.'
Require-Contains $frontend 'db-policy-grid' 'Frontend must have a visual DB policy grid.'
Require-Contains $frontend 'writePolicy' 'Frontend must display backend writePolicy information.'
Require-Contains $frontend '/api/security/access' 'Frontend must load security access for DB policy status.'
Require-Contains $style '.db-policy-grid' 'CSS must style the DB policy grid.'
Require-Contains $style '.db-policy-card' 'CSS must style DB policy cards.'

Require-Contains $plugin 'private void openAdminMap' 'Plugin must include an admin map submenu for cleaner GUI structure.'
Require-Contains $plugin 'open:admin-map' 'Plugin must route to the admin map submenu.'
Require-Contains $plugin 'GUI_SECTION_ELECTIONS' 'Admin map must explicitly document the election section.'
Require-Contains $plugin 'GUI_SECTION_ECONOMY' 'Admin map must explicitly document the economy section.'
Require-Contains $plugin 'GUI_SECTION_PLAYERS' 'Admin map must explicitly document the players section.'
Require-Contains $plugin 'private void openDatabaseHealth' 'Plugin must include a DB health GUI.'
Require-Contains $plugin 'open:db-health' 'Plugin must route to the DB health GUI.'
Require-Contains $plugin 'DB_HEALTH_CHECK' 'Plugin DB health actions must be audited.'
Require-Contains $plugin 'db:optimize' 'Plugin must expose a safe DB optimize action.'
Require-Contains $plugin 'db:checkpoint' 'Plugin must expose a safe WAL checkpoint action.'

if ($errors.Count -gt 0) {
  Write-Host 'DB/GUI/UX plus validation FAILED:' -ForegroundColor Red
  foreach ($e in $errors) { Write-Host " - $e" -ForegroundColor Red }
  exit 1
}

Write-Host 'DB/GUI/UX plus validation passed: DB write policy, web safety UI, admin map, and DB health GUI are wired.' -ForegroundColor Green
