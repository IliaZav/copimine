[CmdletBinding()]
param(
    [string] $SiteRoot = "$(Join-Path (Split-Path -Parent $PSScriptRoot) 'artifacts/launcher/Release/site')",
    [int] $Port = 8181,
    [string] $LauncherPath = "",
    [string] $LocalBindingBaseUrl = ""
)

$ErrorActionPreference = 'Stop'

$site = (Resolve-Path -LiteralPath $SiteRoot -ErrorAction Stop).Path
$metadataPath = Join-Path $site 'assets/public-data/launcher/latest.json'
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "Staged Launcher metadata is missing: $metadataPath"
}
$metadata = Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json
$installerName = [System.IO.Path]::GetFileName([string]$metadata.filename)
if ([string]::IsNullOrWhiteSpace($installerName) -or $installerName -ne [string]$metadata.filename -or $installerName -notmatch '\.exe$') {
    throw "Staged Launcher metadata contains an unsafe installer filename: $($metadata.filename)"
}
foreach ($required in @(
    (Join-Path $site 'launcher/stable/instance-manifest.json'),
    (Join-Path $site 'launcher/stable/instance-manifest.sig'),
    (Join-Path $site "downloads/launcher/$installerName"),
    (Join-Path $site 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe')
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Staged Launcher site is incomplete: $required"
    }
}
$manifest = Get-Content -Raw -LiteralPath (Join-Path $site 'launcher/stable/instance-manifest.json') | ConvertFrom-Json
$runtimeDigest = [string]$manifest.minecraftRuntime.sha256
if ($runtimeDigest -notmatch '^[0-9a-fA-F]{64}$' -or -not (Test-Path -LiteralPath (Join-Path $site "launcher/files/$runtimeDigest") -PathType Leaf)) {
    throw 'Staged Launcher site is missing the server-hosted Minecraft runtime artifact.'
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
    if (-not [string]::IsNullOrWhiteSpace($LocalBindingBaseUrl)) {
        $bindingUri = $null
        if (-not [Uri]::TryCreate($LocalBindingBaseUrl, [UriKind]::Absolute, [ref]$bindingUri)
            -or -not $bindingUri.IsLoopback
            -or -not [string]::Equals($bindingUri.Scheme, [Uri]::UriSchemeHttp, [StringComparison]::OrdinalIgnoreCase)
            -or -not [string]::IsNullOrEmpty($bindingUri.UserInfo)) {
            throw "LocalBindingBaseUrl must be a loopback HTTP URL without credentials."
        }

        $startInfo.Environment['COPIMINE_LAUNCHER_LOCAL_BASE_URL'] = $bindingUri.AbsoluteUri.TrimEnd('/') + '/'
        Write-Output "LOCAL_BINDING_BASE_URL=$($bindingUri.AbsoluteUri.TrimEnd('/'))"
    }
    [System.Diagnostics.Process]::Start($startInfo) | Out-Null
    Write-Output "LAUNCHER_STARTED=$launcher"
} else {
    Write-Output "LAUNCHER_COMMAND=`$env:COPIMINE_LAUNCHER_STAGING_BASE_URL=`"$baseUrl`"; .\CopiMineLauncher.App.exe"
}

Write-Output 'STOP_COMMAND=Stop-Process -Id ' + $server.Id
