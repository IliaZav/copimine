param(
    [string]$Archive = "",
    [string]$TargetRoot = "",
    [string]$TrustedSigningAllowed = ""
)

$ErrorActionPreference = "Stop"

if (-not $Archive) { throw "Usage: rollback.ps1 -Archive <zip> [-TargetRoot <path>]" }
if (-not $TargetRoot) { $TargetRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) { throw "Archive not found: $Archive" }
if ([IO.Path]::GetExtension($Archive) -ne '.zip') { throw 'Windows rollback accepts a verified .zip archive only.' }
if (-not $TrustedSigningAllowed) {
    if ($env:COPIMINE_TRUSTED_SIGNING_ALLOWED) {
        $TrustedSigningAllowed = $env:COPIMINE_TRUSTED_SIGNING_ALLOWED
    } else {
        $TrustedSigningAllowed = Join-Path ([Environment]::GetFolderPath('CommonApplicationData')) 'CopiMine\release-signing.allowed'
    }
}
if (-not (Test-Path -LiteralPath $TrustedSigningAllowed -PathType Leaf)) {
    throw "Trusted release signing allowlist not found: $TrustedSigningAllowed"
}
$trustedKeyItem = Get-Item -LiteralPath $TrustedSigningAllowed -Force
if ($trustedKeyItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw "Trusted release signing allowlist must not be a reparse point: $TrustedSigningAllowed"
}

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

function Get-RelativePathCompat {
    param([string]$Base, [string]$Path)
    $baseUri = [Uri]((Resolve-Path -LiteralPath $Base).Path.TrimEnd('\') + '\')
    $pathUri = [Uri]((Resolve-Path -LiteralPath $Path).Path)
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($pathUri).ToString()).Replace('/', '\')
}

function Assert-SignedPayload {
    param([string]$PayloadRoot, [string]$ManifestPath)
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $entries = @($manifest.payloadFiles)
    if ($entries.Count -eq 0) { throw 'Signed release manifest has no payloadFiles inventory.' }
    $excluded = @('deploy/release_manifest.json', 'deploy/release_manifest.sig', 'deploy/release-signing.allowed')
    $expected = @{}
    foreach ($entry in $entries) {
        $relative = ([string]$entry.path).Replace('\', '/')
        if (-not $relative -or $relative.StartsWith('/') -or $relative.Contains('../') -or $relative -match '^[A-Za-z]:') {
            throw "Unsafe signed payload path: $relative"
        }
        if ($excluded -contains $relative) { throw "Signed payload inventory contains excluded file: $relative" }
        if ($expected.ContainsKey($relative)) { throw "Duplicate signed payload path: $relative" }
        $checksum = ([string]$entry.sha256).ToLowerInvariant()
        if ($checksum -notmatch '^[0-9a-f]{64}$') { throw "Invalid signed payload SHA256: $relative" }
        $size = [int64]$entry.sizeBytes
        if ($size -lt 0) { throw "Invalid signed payload size: $relative" }
        $expected[$relative] = @{ Sha256 = $checksum; Size = $size }
    }

    $actual = @{}
    foreach ($file in (Get-ChildItem -LiteralPath $PayloadRoot -Force -Recurse -File)) {
        if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Signed release payload contains a reparse point: $($file.FullName)" }
        $relative = (Get-RelativePathCompat -Base $PayloadRoot -Path $file.FullName).Replace('\', '/')
        if ($excluded -contains $relative) { continue }
        $actual[$relative] = $file
    }
    $missing = @($expected.Keys | Where-Object { -not $actual.ContainsKey($_) })
    if ($missing.Count -gt 0) { throw "Signed release payload is missing: $($missing -join ', ')" }
    $unexpected = @($actual.Keys | Where-Object { -not $expected.ContainsKey($_) })
    if ($unexpected.Count -gt 0) { throw "Unsigned files found in signed release payload: $($unexpected -join ', ')" }
    foreach ($relative in $expected.Keys) {
        $file = $actual[$relative]
        if ([int64]$file.Length -ne [int64]$expected[$relative].Size) { throw "Signed payload size mismatch: $relative" }
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        if ($actualHash -ne $expected[$relative].Sha256) { throw "Signed payload SHA256 mismatch: $relative" }
    }
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
    $manifestPath = Join-Path $payloadRoot 'deploy\release_manifest.json'
    $signaturePath = Join-Path $payloadRoot 'deploy\release_manifest.sig'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $signaturePath -PathType Leaf)) {
        throw 'Rollback archive is missing its signed release manifest.'
    }
    if (-not (Get-Command ssh-keygen.exe -ErrorAction SilentlyContinue)) { throw 'OpenSSH ssh-keygen.exe is required for signed rollback verification.' }
    Get-Content -LiteralPath $manifestPath -Raw | & ssh-keygen.exe -Y verify -f $TrustedSigningAllowed -I release -n copimine-release -s $signaturePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Rollback release manifest signature verification failed.' }
    Assert-SignedPayload -PayloadRoot $payloadRoot -ManifestPath $manifestPath
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
