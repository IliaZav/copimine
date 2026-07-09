$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $root)
$sourceRoot = Join-Path $root 'copimine-admin-plugin'
$source = Join-Path $sourceRoot 'src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'
$pluginYml = Join-Path $sourceRoot 'plugin.yml'
$plugins = Join-Path $root 'minecraft\server\plugins'
$guide = Join-Path $workspaceRoot 'COPIMINE_TRANSFER_GUIDE.txt'
$index = Join-Path $root 'admin-web\frontend\index.html'
$style = Join-Path $root 'admin-web\frontend\assets\style.css'

$errors = New-Object System.Collections.Generic.List[string]

function Require-Path([string]$path, [string]$message) {
    if (-not (Test-Path -LiteralPath $path)) { $script:errors.Add($message) }
}

function Require-Missing([string]$path, [string]$message) {
    if (Test-Path -LiteralPath $path) { $script:errors.Add($message) }
}

function Require-Text([string]$file, [string]$pattern, [string]$message) {
    if (-not (Test-Path -LiteralPath $file)) {
        $script:errors.Add($message)
        return
    }
    $text = Get-Content -LiteralPath $file -Raw -Encoding UTF8
    if ($text -notmatch $pattern) { $script:errors.Add($message) }
}

Require-Path $sourceRoot 'Clean source folder copimine-admin-plugin is missing.'
Require-Path $source 'Clean AdminPlus source file is missing.'
Require-Path $pluginYml 'Clean AdminPlus plugin.yml is missing.'
Require-Path (Join-Path $plugins 'CopiMineUltimateAdminPlus.jar') 'Active CopiMineUltimateAdminPlus.jar is missing.'
Require-Path (Join-Path $plugins 'CopiMineArtifacts.jar') 'Active CopiMineArtifacts.jar is missing.'
Require-Path (Join-Path $plugins 'CopiMineUltimateAdmin\copimine_ultimate.db') 'Runtime CopiMine DB must stay in plugins\CopiMineUltimateAdmin.'
Require-Path $guide 'Root transfer guide COPIMINE_TRANSFER_GUIDE.txt is missing.'

$legacyRootDirs = @(
    'autheffects-copimine-build',
    'ballot-book-fix-build',
    'ballot-menu-v6-context-final-build',
    'cik-power-patch-build',
    'cik-stations-replace-eye-v12-build',
    'core-cik-shim-build',
    'election-clean-base-build',
    'election-flow-130-rp-build',
    'official-item-guard-build',
    'official-item-guard-storage-fix-build',
    'rp-command-guard-build',
    'seal-drop-fix-build',
    'sidebar-lock-build',
    'station-pro-scope-guard-build',
    'tmp-javap-adminprank',
    'ultra-plus-no-old-elections-build',
    'backups',
    'downloads'
)

foreach ($dir in $legacyRootDirs) {
    Require-Missing (Join-Path $root $dir) "Legacy/root scratch folder must be removed from release package: $dir"
}

Get-ChildItem -LiteralPath $root -File |
    Where-Object { $_.Name -match 'ballot|guard|seal|install_' } |
    ForEach-Object { $errors.Add("Legacy installer/script must be removed from release root: $($_.Name)") }

Require-Missing (Join-Path $plugins 'old-plugins') 'Active server plugins folder must not contain old-plugins archive.'
Require-Missing (Join-Path $plugins 'CopiMineUltimateAdminPlus') 'Stale CopiMineUltimateAdminPlus data folder must be removed; runtime DB is CopiMineUltimateAdmin.'

$activeCopiMineJars = @(Get-ChildItem -LiteralPath $plugins -File -Filter 'CopiMine*.jar' | Select-Object -ExpandProperty Name | Sort-Object)
$requiredCopiMineJars = @(
    'CopiMineArtifacts.jar',
    'CopiMineEconomyCore.jar',
    'CopiMineElectionCore.jar',
    'CopiMineUltimateAdminPlus.jar'
)
foreach ($jar in $requiredCopiMineJars) {
    if ($activeCopiMineJars -notcontains $jar) {
        $errors.Add("Active CopiMine release is missing required jar: $jar")
    }
}
if ($activeCopiMineJars.Count -lt $requiredCopiMineJars.Count) {
    $errors.Add("Base release must expose at least four active CopiMine jars. Found: $($activeCopiMineJars -join ', ')")
}

Get-ChildItem -LiteralPath (Join-Path $root 'admin-web\frontend\assets') -File |
    Where-Object { $_.Name -match '\.before-|^app\.js\.before|^style\.css\.before' } |
    ForEach-Object { $errors.Add("Frontend backup artifact must be removed: $($_.Name)") }

Get-ChildItem -LiteralPath $plugins -File |
    Where-Object { $_.Name -match '\.bak\.|\.before-' } |
    ForEach-Object { $errors.Add("Active plugins folder must not contain jar backup artifact: $($_.Name)") }

Require-Text $index '<html lang="ru">' 'Frontend index must stay Russian-localized.'
Require-Text $index 'login-copy' 'Login screen helper block is missing.'
Require-Text $index 'Аккаунт и файлы сервера' 'Login screen must use concise human-readable account copy.'
Require-Text $style 'release-readiness-card' 'Release readiness visual card styles are missing.'
Require-Text $style 'safety-rail' 'Sensitive-action safety rail styles are missing.'
Require-Text $guide '\b\d{2,3}%' 'Transfer guide must explain project readiness percentage.'
Require-Text $guide 'Ubuntu' 'Transfer guide must include Ubuntu transfer steps.'
Require-Text $guide 'CopiMineUltimateAdminPlus.jar' 'Transfer guide must mention the active AdminPlus plugin jar.'
Require-Text $guide 'CopiMineArtifacts.jar' 'Transfer guide must mention the active Artifacts plugin jar.'
Require-Text $guide 'CopiMineEconomyCore.jar' 'Transfer guide must mention the active EconomyCore plugin jar.'
Require-Text $guide 'CopiMineElectionCore.jar' 'Transfer guide must mention the active ElectionCore plugin jar.'
Require-Text $guide 'FIRST_RUN_ADMIN_GUIDE.md' 'Transfer guide must point to the first-run admin guide.'
Require-Text $guide 'PostgreSQL' 'Transfer guide must mention PostgreSQL as the active auth/data DB.'
Require-Text $guide 'POSTGRES_PASSWORD' 'Transfer guide must explain that PostgreSQL password must be configured before launch.'

if ($errors.Count -gt 0) {
    throw ("Release cleanliness validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Release cleanliness validation passed: modular CopiMine jars, readable UI, and transfer guide are present.'


