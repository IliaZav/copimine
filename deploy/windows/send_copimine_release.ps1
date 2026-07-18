[CmdletBinding()]
param(
    [string]$Archive = '',
    [string]$ServerName = 'server-rpgrp',
    [string]$ServerIp = '100.108.97.11',
    [string]$Username = 'qwerty',
    [int]$Port = 22,
    [string]$RemoteDir = '/home/qwerty/copimine-upload'
)

$ErrorActionPreference = 'Stop'
foreach ($command in @('ssh', 'scp')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "OpenSSH command not found: $command" }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $Archive) {
    $workspaceRelease = Join-Path (Split-Path (Split-Path $repoRoot -Parent) -Parent) 'release'
    $latest = Get-ChildItem -LiteralPath $workspaceRelease -Filter 'copimine-opt-full-*.tar.gz' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) { throw "Release archive not found in $workspaceRelease" }
    $Archive = $latest.FullName
}
$archivePath = (Resolve-Path -LiteralPath $Archive).Path
$archiveName = Split-Path -Leaf $archivePath
$archiveDir = Split-Path -Parent $archivePath
$shaPath = "$archivePath.sha256"
$bootstrapPath = Join-Path $archiveDir ($archiveName -replace '\.gz$', '.bootstrap.json')
$installer = Join-Path $repoRoot 'deploy\ubuntu\install_release.sh'
$diagnostics = Join-Path $repoRoot 'deploy\ubuntu\collect_deploy_diagnostics.sh'
foreach ($required in @($shaPath, $bootstrapPath, $installer, $diagnostics)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required file not found: $required" }
}
$expected = (Get-Content -LiteralPath $shaPath -Raw).Trim().Split()[0].ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
if ($expected -ne $actual) { throw "SHA256 mismatch: expected $expected, got $actual" }

$remote = "$Username@$ServerIp"
Write-Host "Server: $ServerName (${remote}:$Port)"
Write-Host "Archive: $archiveName"
Write-Host "SHA256: $actual"
& ssh -p $Port $remote "mkdir -p '$RemoteDir'"
if ($LASTEXITCODE) { throw 'Remote directory creation failed.' }
& scp -P $Port $archivePath $shaPath $bootstrapPath $installer $diagnostics "${remote}:$RemoteDir/"
if ($LASTEXITCODE) { throw 'Upload failed.' }

$remoteCheck = "cd '$RemoteDir' && expected=`$(awk '{print tolower(`$1)}' '$archiveName.sha256' | head -n1 | tr -d '\\r\\n') && actual=`$(sha256sum '$archiveName' | awk '{print tolower(`$1)}' | tr -d '\\r\\n') && test `"`$expected`" = `"`$actual`""
& ssh -p $Port $remote $remoteCheck
if ($LASTEXITCODE) { throw 'Remote checksum verification failed.' }

Write-Host ''
Write-Host 'Upload verified. Existing data is not changed.'
Write-Host "Install command: sudo bash '$RemoteDir/install_release.sh' '$RemoteDir/$archiveName'"
Write-Host "Diagnostics command: sudo bash '$RemoteDir/collect_deploy_diagnostics.sh'"
