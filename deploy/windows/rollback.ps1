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

try {
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
    Move-Item -LiteralPath $targetPath -Destination $backupPath
    $movedCurrent = $true
    Move-Item -LiteralPath $payloadRoot -Destination $targetPath
    $movedCurrent = $false
    Write-Host "Rollback restored into $targetPath"
    Write-Host "Previous files are retained at $backupPath"
    Write-Host 'PostgreSQL is intentionally not changed by the Windows file rollback.'
}
catch {
    if ($movedCurrent -and -not (Test-Path -LiteralPath $targetPath) -and (Test-Path -LiteralPath $backupPath)) {
        Move-Item -LiteralPath $backupPath -Destination $targetPath -ErrorAction SilentlyContinue
    }
    throw
}
finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
