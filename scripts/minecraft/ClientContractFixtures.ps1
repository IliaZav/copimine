[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Prepare','Restore')][string]$Action,
    [Parameter(Mandatory=$true)][string]$WorkspaceRoot,
    [Parameter(Mandatory=$true)][string]$BackupDirectory
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $WorkspaceRoot).Path
. (Join-Path $root 'scripts/minecraft/MigrationOutputSnapshot.ps1')
# Exact generated release outputs; source files and installed runtimes are not leases.
$paths=@(
    'thirdparty/client-mods/CopiMineClient-0.1.1.jar',
    'thirdparty/thirdparty_manifest.json', 'thirdparty/checksums.txt',
    'thirdparty/CopiMineMods.zip', 'thirdparty/CopiMineMods.sha1',
    'thirdparty/CopiMineMods.sha256', 'thirdparty/_modpack_stage',
    'admin-web/frontend/assets/public-data/modpack_snapshot.json'
)
$receiptPath=Join-Path $BackupDirectory 'client-fixtures-receipt.json'
$manifestPath=Join-Path $BackupDirectory 'manifest.json'
$head=(& git -C $root rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$'){throw 'Cannot identify fixture source commit'}
if($Action -eq 'Restore'){
    if(-not(Test-Path -LiteralPath $receiptPath -PathType Leaf)){
        if(Test-Path -LiteralPath $manifestPath){throw 'Fixture snapshot exists without a verified receipt'}
        Write-Host 'Client contract fixtures were not prepared.'
        return
    }
    $receipt=Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
    if($receipt.sourceCommit -cne $head){throw 'Source changed during client fixture lease; restoration withheld'}
    $snapshot=Read-MigrationOutputSnapshot -ManifestPath $manifestPath -WorkspaceRoot $root -ExpectedRelativePaths $paths -ExpectedManifestSha256 $receipt.manifestSha256
    Restore-MigrationOutputSnapshot -Snapshot $snapshot
    if(-not(Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $snapshot)){throw 'Client fixture restoration differs from backup'}
    Write-Host 'Restored and verified original client contract fixtures.'
    return
}
if((Test-Path -LiteralPath $manifestPath) -or (Test-Path -LiteralPath $receiptPath)){throw 'Client fixture backup already exists; restore that lease first'}
$snapshot=New-MigrationOutputSnapshot -WorkspaceRoot $root -SnapshotDirectory $BackupDirectory -RelativePaths $paths
$receipt=[ordered]@{sourceCommit=$head;manifestSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $snapshot.ManifestPath).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText($receiptPath,($receipt | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
# The receipt exists before promotion so an interrupted or failed sync can roll back.
& (Join-Path $root 'scripts/thirdparty/build_modpack.ps1') -ProjectRoot $root -SyncBuiltClient
if($LASTEXITCODE -ne 0){throw "Client fixture sync failed: $LASTEXITCODE"}
$built=Join-Path $root 'CopiMineClient/build/libs/CopiMineClient-0.1.1.jar'
$staged=Join-Path $root $paths[0]
$expected=(Get-FileHash -Algorithm SHA256 -LiteralPath $built).Hash
if((Get-FileHash -Algorithm SHA256 -LiteralPath $staged).Hash -cne $expected){throw 'Staged client differs from the compiler output'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead((Join-Path $root 'thirdparty/CopiMineMods.zip'))
try {
    $entry=$archive.GetEntry('mods/CopiMineClient-0.1.1.jar')
    if($null -eq $entry){throw 'Modpack has no rebuilt client'}
    $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
    try {$embedded=[BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','')}
    finally {$stream.Dispose();$sha.Dispose()}
    if($embedded -cne $expected){throw 'Modpack client differs from the compiler output'}
} finally {$archive.Dispose()}
Write-Host "Verified rebuilt client, staged JAR and modpack payload: $expected"
