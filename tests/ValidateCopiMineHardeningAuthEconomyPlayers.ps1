$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$plugin = Join-Path $root "copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java"
$backend = Join-Path $root "admin-web\backend\main.py"
$frontend = Join-Path $root "admin-web\frontend\assets\app.js"
$style = Join-Path $root "admin-web\frontend\assets\style.css"

$errors = New-Object System.Collections.Generic.List[string]

function Require-Text([string]$name, [string]$text, [string]$pattern) {
    if ($text -notmatch $pattern) { $errors.Add("$name missing: $pattern") }
}

$java = Get-Content -LiteralPath $plugin -Raw -Encoding UTF8
$py = Get-Content -LiteralPath $backend -Raw -Encoding UTF8
$js = Get-Content -LiteralPath $frontend -Raw -Encoding UTF8
$css = Get-Content -LiteralPath $style -Raw -Encoding UTF8

Require-Text "backend postgres host" $py "POSTGRES_HOST"
Require-Text "backend postgres schema" $py "POSTGRES_SCHEMA"
Require-Text "backend auth db schema" $py "cm_admin_users"
Require-Text "backend session db schema" $py "cm_admin_sessions"
Require-Text "backend auth db init" $py "ensure_auth_db"
Require-Text "backend cookie set" $py "set_cookie"
Require-Text "backend cookie read" $py "cookies\.get\(AUTH_COOKIE_NAME\)"
Require-Text "backend cookie clear" $py "delete_cookie"
Require-Text "backend sensitive confirm" $py "require_sensitive_confirm"

Require-Text "frontend cookie credentials" $js 'credentials:\s*"include"'
Require-Text "frontend safe confirm" $js "dangerConfirm"
Require-Text "frontend admin create panel" $js "admin-create-panel"
Require-Text "frontend admin create function" $js "createAdminUser"
Require-Text "frontend auth badge" $js "cookieAuth"
Require-Text "frontend safety styling" $css "danger-zone"

Require-Text "plugin economy health menu" $java "openEconomyHealth"
Require-Text "plugin economy health action" $java "open:ar-health"
Require-Text "plugin AR guard incidents table" $java "cmv7_ar_guard_incidents"
Require-Text "plugin AR guard incident writer" $java "recordArGuardIncident"
Require-Text "plugin mass save all" $java "players:save-all"
Require-Text "plugin mass night vision all" $java "players:night-vision-all"
Require-Text "plugin mass cleanse all" $java "players:clear-negative-effects-all"
Require-Text "plugin nameplate polish" $java "updateRoleNameplates"
Require-Text "plugin tab name polish" $java "setPlayerListName"

Require-Text "backend auth uses psycopg" $py "psycopg.connect"

if ($errors.Count -gt 0) {
    Write-Host "Hardening/auth/economy/player validation FAILED:" -ForegroundColor Red
    foreach ($e in $errors) { Write-Host " - $e" -ForegroundColor Red }
    exit 1
}

Write-Host "Hardening/auth/economy/player validation passed." -ForegroundColor Green
