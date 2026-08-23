[CmdletBinding()]
param(
    [int] $Port = 8091,
    [string] $ValidationRoot = "$(Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/local-validation')",
    [string] $PythonPath = "$(Join-Path (Split-Path -Parent $PSScriptRoot) '.audit-venv313/Scripts/python.exe')"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $ValidationRoot -ErrorAction Stop).Path
$bindingRoot = Join-Path $root 'binding-staging-1.0.3'
$adminData = Join-Path $bindingRoot 'admin-data'
$serverRoot = Join-Path $bindingRoot 'server'
$worldRoot = Join-Path $serverRoot 'world'
$logsRoot = Join-Path $serverRoot 'logs'
$controlRoot = Join-Path $bindingRoot 'control'
$publicRoot = Join-Path $bindingRoot 'public'
$resolvedPython = (Resolve-Path -LiteralPath $PythonPath -ErrorAction Stop).Path
$adminRoot = (Resolve-Path -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'admin-web') -ErrorAction Stop).Path

foreach ($path in @($adminData, (Join-Path $worldRoot 'playerdata'), (Join-Path $worldRoot 'stats'), (Join-Path $worldRoot 'advancements'), $logsRoot, $controlRoot, $publicRoot)) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
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
$env:COPIMINE_LAUNCHER_SOURCE_MANIFEST = Join-Path $bindingRoot 'source-manifest.json'
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
Write-Output "STOP_COMMAND=Stop-Process -Id $($process.Id)"
