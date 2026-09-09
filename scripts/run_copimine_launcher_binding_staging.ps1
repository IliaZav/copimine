[CmdletBinding()]
param(
    [int] $Port = 8090,
    [string] $ValidationRoot = "",
    [string] $PythonPath = "",
    [string] $PublicRoot = "",
    [string] $LauncherPath = "",
    [string] $LocalBindingBaseUrl = ""
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
if ([string]::IsNullOrWhiteSpace($ValidationRoot)) {
    $ValidationRoot = Join-Path $repoRoot 'artifacts/local-validation'
}
if ([string]::IsNullOrWhiteSpace($PythonPath)) {
    $PythonPath = Join-Path $repoRoot '.audit-venv313/Scripts/python.exe'
}
$null = New-Item -ItemType Directory -Path $ValidationRoot -Force
$root = (Resolve-Path -LiteralPath $ValidationRoot -ErrorAction Stop).Path
$bindingRoot = Join-Path $root 'binding-staging-1.0.3'
$adminData = Join-Path $bindingRoot 'admin-data'
$serverRoot = Join-Path $bindingRoot 'server'
$worldRoot = Join-Path $serverRoot 'world'
$logsRoot = Join-Path $serverRoot 'logs'
$controlRoot = Join-Path $bindingRoot 'control'
$publicRoot = if ([string]::IsNullOrWhiteSpace($PublicRoot)) {
    Join-Path $bindingRoot 'public'
} else {
    (Resolve-Path -LiteralPath $PublicRoot -ErrorAction Stop).Path
}
$resolvedPython = (Resolve-Path -LiteralPath $PythonPath -ErrorAction Stop).Path
$adminRoot = (Resolve-Path -LiteralPath (Join-Path $repoRoot 'admin-web') -ErrorAction Stop).Path

foreach ($path in @($adminData, (Join-Path $worldRoot 'playerdata'), (Join-Path $worldRoot 'stats'), (Join-Path $worldRoot 'advancements'), $logsRoot, $controlRoot)) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}
if (-not (Test-Path -LiteralPath $publicRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $publicRoot -Force | Out-Null
}
$latestLog = Join-Path $logsRoot 'latest.log'
if (-not (Test-Path -LiteralPath $latestLog -PathType Leaf)) {
    Set-Content -LiteralPath $latestLog -Value '[INFO] disposable binding staging' -Encoding UTF8
}

$databasePath = Join-Path $adminData 'auth.sqlite3'
$databaseUrl = 'sqlite:///' + ($databasePath -replace '\\', '/')
$baseUrl = "http://127.0.0.1:$Port"
$env:COPIMINE_STARTUP_STRICT = '0'
$env:SECRET_KEY = 'local-binding-staging-secret-0123456789-abcdefghijklmnopqrstuvwxyz'
$env:COPIMINE_AUTH_STORAGE = 'sqlite'
$env:COPIMINE_AUTH_DB = $databasePath
$env:DATABASE_URL = $databaseUrl
$env:POSTGRES_PASSWORD = ''
$env:PGPASSWORD = ''
$env:ADMIN_PUBLIC_BASE_URL = $baseUrl
$env:ALLOW_INSECURE_HTTP_AUTH = '1'
$env:COPIMINE_ADMIN_DATA = $adminData
$env:MC_SERVER_DIR = $serverRoot
$env:MC_WORLD_DIR = $worldRoot
$env:MC_LOG_FILE = $latestLog
$env:COPIMINE_LAUNCHER_CONTROL_DIR = $controlRoot
$env:COPIMINE_LAUNCHER_PUBLIC_ROOT = $publicRoot
$env:COPIMINE_FRONTEND_ROOT = $publicRoot
$sourceManifest = Join-Path $publicRoot 'launcher/stable/instance-manifest.json'
if (-not (Test-Path -LiteralPath $sourceManifest -PathType Leaf)) {
    $sourceManifest = Join-Path $bindingRoot 'source-manifest.json'
}
$env:COPIMINE_LAUNCHER_SOURCE_MANIFEST = $sourceManifest
$env:COPIMINE_LAUNCHER_DOWNLOAD_ORIGIN = "$baseUrl/launcher/files"

$stdout = Join-Path $bindingRoot 'backend.stdout.log'
$stderr = Join-Path $bindingRoot 'backend.stderr.log'
$process = Start-Process -FilePath $resolvedPython `
    -ArgumentList @('-m', 'uvicorn', 'backend.main:app', '--host', '127.0.0.1', '--port', [string]$Port, '--log-level', 'warning') `
    -WorkingDirectory $adminRoot `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

Write-Output "BINDING_STAGING_BASE_URL=$baseUrl"
Write-Output "BINDING_STAGING_PID=$($process.Id)"
Write-Output "BINDING_STAGING_ROOT=$bindingRoot"
Write-Output "BINDING_STAGING_PUBLIC_ROOT=$publicRoot"

if (-not [string]::IsNullOrWhiteSpace($LauncherPath)) {
    $launcher = (Resolve-Path -LiteralPath $LauncherPath -ErrorAction Stop).Path
    $bindingBaseUrl = $baseUrl
    if (-not [string]::IsNullOrWhiteSpace($LocalBindingBaseUrl)) {
        $bindingUri = $null
        $bindingIsValid = [Uri]::TryCreate($LocalBindingBaseUrl, [UriKind]::Absolute, [ref]$bindingUri) -and
            $bindingUri.IsLoopback -and
            [string]::Equals($bindingUri.Scheme, [Uri]::UriSchemeHttp, [StringComparison]::OrdinalIgnoreCase) -and
            [string]::IsNullOrEmpty($bindingUri.UserInfo)
        if (-not $bindingIsValid) {
            throw "LocalBindingBaseUrl must be a loopback HTTP URL without credentials."
        }
        $bindingBaseUrl = $bindingUri.AbsoluteUri.TrimEnd('/')
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $launcher
    $startInfo.WorkingDirectory = Split-Path -Parent $launcher
    $startInfo.UseShellExecute = $false
    $startInfo.Environment['COPIMINE_LAUNCHER_STAGING_BASE_URL'] = $baseUrl
    $startInfo.Environment['COPIMINE_LAUNCHER_LOCAL_BASE_URL'] = $bindingBaseUrl
    [System.Diagnostics.Process]::Start($startInfo) | Out-Null
    Write-Output "LAUNCHER_STARTED=$launcher"
    Write-Output "LOCAL_BINDING_BASE_URL=$bindingBaseUrl"
}

Write-Output "STOP_COMMAND=Stop-Process -Id $($process.Id)"
