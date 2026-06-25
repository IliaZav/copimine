$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginSource = Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $root 'copimine-admin-plugin\plugin.yml'
$backend = Join-Path $root 'admin-web\backend\main.py'
$frontend = Join-Path $root 'admin-web\frontend\assets\app.js'
$style = Join-Path $root 'admin-web\frontend\assets\style.css'
$plugins = Join-Path $root 'minecraft\server\plugins'
$serverProperties = Join-Path $root 'minecraft\server\server.properties'

$errors = New-Object System.Collections.Generic.List[string]

function Text([string]$path) { (Get-Content -LiteralPath $path -Raw -Encoding UTF8) -replace "`r", "" }
function Require([string]$name, [string]$text, [string]$pattern) {
    if ($text -notmatch $pattern) { $script:errors.Add("$name missing: $pattern") }
}
function RequirePath([string]$path, [string]$message) {
    if (-not (Test-Path -LiteralPath $path)) { $script:errors.Add($message) }
}

$java = Text $pluginSource
$yml = Text $pluginYml
$py = Text $backend
$js = Text $frontend
$css = Text $style
$props = Text $serverProperties

Require "plugin version" $yml "9\.1\.0-postgres-v4"
Require "plugin enable version" $java "9\.1\.0-postgres-v4"
Require "startup checks table" $java "cmv8_startup_checks"
Require "startup check rows" $java "startupSelfCheckRows"
Require "startup GUI" $java "openStartupReadiness"
Require "startup repair" $java "runStartupSelfHeal"
Require "startup audit" $java "STARTUP_SELF_CHECK"
Require "startup action" $java "open:startup-readiness"
Require "startup repair action" $java "startup:repair"
Require "startup dependency checks" $java "Chunky.*SeeMore|SeeMore.*Chunky"
Require "admin hub startup button" $java "startup-readiness"

Require "backend performance readiness endpoint" $py '@app\.get\("/api/performance/readiness"\)'
Require "backend performance readiness function" $py "performance_readiness_sync"
Require "backend Chunky check" $py "Chunky"
Require "backend SeeMore check" $py "SeeMore"
Require "backend resource pack mojibake check" $py "resourcePackPromptReadable"

Require "frontend first-run readiness" $js "firstRunReadinessHtml"
Require "frontend optimization stack" $js "optimizationStackHtml"
Require "frontend performance readiness api" $js "/api/performance/readiness"
Require "frontend startup UX marker" $js "first-run-ready"
Require "frontend optimization CSS" $css "startup-grid"
Require "frontend optimization CSS detail" $css "optimization-stack"

RequirePath (Join-Path $plugins 'Chunky-Bukkit-1.4.40.jar') 'Chunky optimization plugin jar is missing.'
RequirePath (Join-Path $plugins 'SeeMore-1.0.2.jar') 'SeeMore optimization plugin jar is missing.'
RequirePath (Join-Path $plugins 'Chunky\config.yml') 'Chunky config.yml is missing.'
RequirePath (Join-Path $plugins 'SeeMore\config.yml') 'SeeMore config.yml is missing.'

Require "server properties tuned max chain" $props '(?m)^max-chained-neighbor-updates=100000$'
Require "server properties readable pack prompt" $props "CopiMine"
Require "server properties compression" $props '(?m)^network-compression-threshold=512$'

if ($errors.Count -gt 0) {
    throw ("First-run/performance validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'First-run/performance validation passed: plugin startup checks, web readiness, optimization plugins, and config tuning are present.'
