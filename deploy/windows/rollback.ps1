param([string]$Archive = "", [string]$TargetRoot = "")

$ErrorActionPreference = "Stop"

if (-not $Archive) { throw "Usage: rollback.ps1 -Archive <zip> [-TargetRoot <path>]" }
if (-not $TargetRoot) { $TargetRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) { throw "Archive not found: $Archive" }
if ([IO.Path]::GetExtension($Archive) -ne '.zip') { throw 'Windows rollback accepts a verified .zip archive only.' }

$archivePath = (Resolve-Path -LiteralPath $Archive).Path
$targetPath = (Resolve-Path -LiteralPath $TargetRoot).Path
$shaPath = "$archivePath.sha256"
if (-not (Test-Path -LiteralPath $shaPath -PathType Leaf)) { throw "SHA256 sidecar not found: $shaPath" }
$expected = (Get-Content -LiteralPath $shaPath -Raw -Encoding ascii).Trim().Split()[0].ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
if ($expected -notmatch '^[0-9a-f]{64}$' -or $expected -ne $actual) { throw 'Rollback archive SHA256 verification failed.' }

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("copimine-windows-rollback-" + [guid]::NewGuid().ToString())
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupPath = "$targetPath.pre-rollback-$timestamp"
$movedCurrent = $false
$services = @('copimine-admin','copimine-minecraft','copimine-discord-bot','copimine-minecraft-discord-bridge')
$startedServices = @()

function Stop-CopiMineServices {
    foreach ($name in $services) {
        $svc = Get-Service -Name $name -ErrorAction SilentlyContinue
        if ($svc -and $svc.Status -eq 'Running') {
            Stop-Service -Name $name -Force -ErrorAction Stop
            (Get-Service -Name $name).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
            $script:startedServices += $name
        }
    }
}

function Start-CopiMineServices {
    foreach ($name in $startedServices) {
        Start-Service -Name $name -ErrorAction Stop
    }
}

function Assert-SafeZip {
    param([string]$Path)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName.Replace('\','/')
            if (-not $name -or $name.StartsWith('/') -or $name.Split('/') -contains '..' -or $name -match '^[A-Za-z]:') { throw "Unsafe rollback archive path: $name" }
            if (-not $seen.Add($name)) { throw "Duplicate rollback archive path: $name" }
            $unixType = ($entry.ExternalAttributes -shr 16) -band 0xF000
            if ($unixType -and $unixType -notin @(0x8000,0x4000)) { throw "Special rollback archive entry: $name" }
        }
    }
    finally { $zip.Dispose() }
}

try {
    Assert-SafeZip $archivePath
    Expand-Archive -LiteralPath $archivePath -DestinationPath $tempRoot -Force
    $payloadRoot = Join-Path $tempRoot 'copimine'
    if (-not (Test-Path -LiteralPath $payloadRoot -PathType Container)) {
        throw "Rollback archive must contain a top-level copimine directory."
    }
    foreach ($required in @('admin-web', 'minecraft', 'deploy')) {
        if (-not (Test-Path -LiteralPath (Join-Path $payloadRoot $required))) {
            throw "Rollback archive is incomplete: missing $required"
        }
    }
    Stop-CopiMineServices
    Move-Item -LiteralPath $targetPath -Destination $backupPath
    $movedCurrent = $true
    Move-Item -LiteralPath $payloadRoot -Destination $targetPath
    if (-not (Test-Path -LiteralPath (Join-Path $targetPath 'admin-web\backend\main.py')) -or
        -not (Test-Path -LiteralPath (Join-Path $targetPath 'minecraft\server\server.properties'))) {
        throw 'Restored runtime failed post-swap validation.'
    }
    $movedCurrent = $false
    Write-Host "Rollback restored into $targetPath"
    Write-Host "Previous files are retained at $backupPath"
    Write-Host 'PostgreSQL is intentionally not changed by the Windows file rollback.'
}
catch {
    if ($movedCurrent) {
        $failedPath = "$targetPath.failed-rollback-$timestamp"
        if (Test-Path -LiteralPath $targetPath) {
            Move-Item -LiteralPath $targetPath -Destination $failedPath -ErrorAction SilentlyContinue
        }
        if (-not (Test-Path -LiteralPath $targetPath) -and (Test-Path -LiteralPath $backupPath)) {
            Move-Item -LiteralPath $backupPath -Destination $targetPath -ErrorAction SilentlyContinue
        }
    }
    throw
}
finally {
    try { Start-CopiMineServices } catch { Write-Warning "Could not restart services: $($_.Exception.Message)" }
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
