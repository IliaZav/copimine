[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallRoot,
    [Parameter(Mandatory = $true)]
    [string] $SiteRoot,
    [int] $Port = 8281,
    [int] $TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$installRoot = (Resolve-Path -LiteralPath $InstallRoot -ErrorAction Stop).Path
$siteRoot = (Resolve-Path -LiteralPath $SiteRoot -ErrorAction Stop).Path
$launcherPath = Join-Path $installRoot 'current/CopiMineLauncher.App.exe'
if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
    throw "Installed Launcher executable is missing: $launcherPath"
}

$python = Get-Command python -ErrorAction Stop
$server = Start-Process -FilePath $python.Source `
    -ArgumentList @('-m', 'http.server', [string]$Port, '--bind', '127.0.0.1', '--directory', $siteRoot) `
    -WindowStyle Hidden `
    -PassThru
$app = $null
try {
    $env:COPIMINE_LAUNCHER_STAGING_BASE_URL = "http://127.0.0.1:$Port"
    $app = Start-Process -FilePath $launcherPath `
        -WorkingDirectory (Split-Path -Parent $launcherPath) `
        -WindowStyle Hidden `
        -PassThru

    # Velopack replaces only the current application directory during an
    # update. The packaged default uses a Launcher subdirectory and keeps the
    # game beside it; a custom root selected directly keeps the game inside
    # that selected root.
    $installName = Split-Path -Leaf $installRoot
    $minecraftRoot = if ($installName -ieq 'Launcher') {
        Join-Path (Split-Path -Parent $installRoot) 'Minecraft'
    } else {
        Join-Path $installRoot 'Minecraft'
    }
    $managedState = Join-Path $minecraftRoot '.copimine/managed-state.json'
    $serversDat = Join-Path $minecraftRoot 'servers.dat'
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $prepared = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $hasManagedState = Test-Path -LiteralPath $managedState -PathType Leaf
        $hasServersDat = Test-Path -LiteralPath $serversDat -PathType Leaf
        if ($hasManagedState -and $hasServersDat) {
            $prepared = $true
            break
        }

        if ($app.HasExited) {
            break
        }
    }

    $fileCount = if (Test-Path -LiteralPath $minecraftRoot) {
        (Get-ChildItem -LiteralPath $minecraftRoot -File -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count
    } else {
        0
    }
    $modCount = if (Test-Path -LiteralPath (Join-Path $minecraftRoot 'mods')) {
        (Get-ChildItem -LiteralPath (Join-Path $minecraftRoot 'mods') -File -Filter '*.jar' | Measure-Object).Count
    } else {
        0
    }

    $result = [pscustomobject]@{
        InstallRoot = $installRoot
        MinecraftRoot = $minecraftRoot
        LauncherPid = $app.Id
        LauncherExited = $app.HasExited
        LauncherExitCode = if ($app.HasExited) { $app.ExitCode } else { $null }
        Prepared = $prepared
        ManagedState = Test-Path -LiteralPath $managedState -PathType Leaf
        Mods = $modCount
        ServersDat = Test-Path -LiteralPath $serversDat -PathType Leaf
        FileCount = $fileCount
    }
    $result | Format-List
    if (-not $prepared) {
        throw "Clean-machine Launcher preparation did not reach managed-state + servers.dat before timeout."
    }
}
finally {
    if ($null -ne $app -and -not $app.HasExited) {
        Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
        $app.WaitForExit(5000) | Out-Null
    }
    if ($null -ne $server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
        $server.WaitForExit(5000) | Out-Null
    }
    Remove-Item Env:COPIMINE_LAUNCHER_STAGING_BASE_URL -ErrorAction SilentlyContinue
}
