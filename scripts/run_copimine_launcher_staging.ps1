[CmdletBinding()]
param(
    [string] $SiteRoot = "$(Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/launcher/Release/site')",
    [int] $Port = 8181,
    [string] $LauncherPath = ""
)

$ErrorActionPreference = 'Stop'

$site = (Resolve-Path -LiteralPath $SiteRoot -ErrorAction Stop).Path
foreach ($required in @(
    (Join-Path $site 'launcher/stable/instance-manifest.json'),
    (Join-Path $site 'launcher/stable/instance-manifest.sig'),
    (Join-Path $site 'downloads/launcher/CopiMineLauncherSetup-1.0.0.exe')
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Staged Launcher site is incomplete: $required"
    }
}

$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) {
    throw 'Python is required to serve the local staged site.'
}

$baseUrl = "http://127.0.0.1:$Port"
$server = Start-Process -FilePath $python.Source `
    -ArgumentList @('-m', 'http.server', [string]$Port, '--bind', '127.0.0.1', '--directory', $site) `
    -WindowStyle Hidden `
    -PassThru

Write-Output "STAGING_SITE=$site"
Write-Output "STAGING_BASE_URL=$baseUrl"
Write-Output "STAGING_SERVER_PID=$($server.Id)"

if (-not [string]::IsNullOrWhiteSpace($LauncherPath)) {
    $launcher = (Resolve-Path -LiteralPath $LauncherPath -ErrorAction Stop).Path
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $launcher
    $startInfo.WorkingDirectory = Split-Path -Parent $launcher
    $startInfo.UseShellExecute = $false
    $startInfo.Environment['COPIMINE_LAUNCHER_STAGING_BASE_URL'] = $baseUrl
    [System.Diagnostics.Process]::Start($startInfo) | Out-Null
    Write-Output "LAUNCHER_STARTED=$launcher"
} else {
    Write-Output 'LAUNCHER_COMMAND=$env:COPIMINE_LAUNCHER_STAGING_BASE_URL="http://127.0.0.1:8181"; .\CopiMineLauncher.App.exe'
}

Write-Output 'STOP_COMMAND=Stop-Process -Id ' + $server.Id
