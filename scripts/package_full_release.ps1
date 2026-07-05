param(
    [string]$ProjectRoot = "",
    [string]$ReleaseDir = "",
    [string]$DbDumpPath = ""
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
}
$ProjectRoot = (Resolve-Path $ProjectRoot).Path

if (-not $ReleaseDir) {
    $workspaceRoot = Split-Path (Split-Path $ProjectRoot -Parent) -Parent
    $ReleaseDir = Join-Path $workspaceRoot "release"
}
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

$timestamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$clientBuildScript = Join-Path $ProjectRoot "CopiMineClient\build-client.ps1"
$clientJar = Join-Path $ProjectRoot "CopiMineClient\build\libs\CopiMineClient-0.1.0.jar"
$thirdpartyClientJar = Join-Path $ProjectRoot "thirdparty\client-mods\CopiMineClient-0.1.0.jar"
$buildModpackScript = Join-Path $ProjectRoot "scripts\thirdparty\build_modpack.ps1"
$resourcepackScript = Join-Path $ProjectRoot "resourcepacks\build-resourcepack.py"
$resourcepackZip = Join-Path $ProjectRoot "resourcepacks\build\CopiMineResourcePack.zip"
$resourcepackShaFile = Join-Path $ProjectRoot "resourcepacks\build\CopiMineResourcePack.sha1"
$resourcepackSha256File = Join-Path $ProjectRoot "resourcepacks\build\CopiMineResourcePack.sha256"
$modpackZip = Join-Path $ProjectRoot "thirdparty\CopiMineMods.zip"
$modpackShaFile = Join-Path $ProjectRoot "thirdparty\CopiMineMods.sha1"
$modpackSha256File = Join-Path $ProjectRoot "thirdparty\CopiMineMods.sha256"
$modpackSnapshotPath = Join-Path $ProjectRoot "admin-web\frontend\assets\public-data\modpack_snapshot.json"
$thirdpartyManifestPath = Join-Path $ProjectRoot "thirdparty\thirdparty_manifest.json"
$releaseManifestPath = Join-Path $ProjectRoot "deploy\release_manifest.json"
$installerManifestPath = Join-Path $ProjectRoot "deploy\installer_manifest.json"
$deployInstall = Join-Path $ProjectRoot "deploy\ubuntu\install.sh"
$deployUpdate = Join-Path $ProjectRoot "deploy\ubuntu\update.sh"
$deployVerify = Join-Path $ProjectRoot "deploy\ubuntu\verify.sh"
$validateReleaseScript = Join-Path $ProjectRoot "scripts\validate_release_bundle.ps1"
$embeddedUnpackScript = Join-Path $ProjectRoot "deploy\ubuntu\copimine_unpack_and_verify.sh"
$embeddedReplaceScript = Join-Path $ProjectRoot "deploy\ubuntu\copimine_full_replace.sh"
$embeddedCommonScript = Join-Path $ProjectRoot "deploy\shared\common.sh"
$serverPropertiesPath = Join-Path $ProjectRoot "minecraft\server\server.properties"
$resourcePackDownloadUrl = "http://admin.copimine.ru:18080/resourcepacks/CopiMineResourcePack.zip"
$modpackDownloadUrl = "/downloads/CopiMineMods.zip"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @()
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $FilePath $($Arguments -join ' ')"
    }
}

function Get-Sha256Lower {
    param([Parameter(Mandatory = $true)][string]$LiteralPath)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $LiteralPath).Hash.ToLowerInvariant()
}

function Write-Checksums {
    $entries = @(
        "thirdparty\client-mods\CopiMineClient-0.1.0.jar",
        "thirdparty\client-mods\emotecraft-for-MC1.21.1-2.4.12-fabric.jar",
        "thirdparty\client-mods\fabric-api-0.116.11+1.21.1.jar",
        "thirdparty\client-mods\voicechat-fabric-1.21.1-2.6.16.jar",
        "thirdparty\client-mods\iris-fabric-1.8.8+mc1.21.1.jar",
        "thirdparty\client-mods\sodium-fabric-0.6.13+mc1.21.1.jar",
        "thirdparty\server-plugins\CoreProtect-CE-23.0.jar",
        "thirdparty\server-plugins\emotecraft-2.4.12-bukkit.jar"
    )
    $lines = foreach ($relative in $entries) {
        $full = Join-Path $ProjectRoot $relative
        if (-not (Test-Path -LiteralPath $full)) {
            throw "Missing checksum file: $relative"
        }
        $sha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $full).Hash.ToLowerInvariant()
        "SHA1  $($relative -replace '\\','/')  $sha1"
    }
    Set-Content -LiteralPath (Join-Path $ProjectRoot "thirdparty\checksums.txt") -Value ($lines -join "`n") -Encoding ascii
}

Write-Host "[1/8] Build CopiMineClient"
Invoke-Checked -FilePath "powershell" -Arguments @("-ExecutionPolicy", "Bypass", "-File", $clientBuildScript)
if (-not (Test-Path -LiteralPath $clientJar)) {
    throw "Missing built client jar: $clientJar"
}

Write-Host "[2/8] Sync client jar into site modpack inputs"
Copy-Item -LiteralPath $clientJar -Destination $thirdpartyClientJar -Force
Write-Checksums

Write-Host "[3/8] Build site modpack archive"
Invoke-Checked -FilePath "powershell" -Arguments @("-ExecutionPolicy", "Bypass", "-File", $buildModpackScript, "-ProjectRoot", $ProjectRoot)

Write-Host "[4/8] Build managed resource pack"
Invoke-Checked -FilePath "python" -Arguments @($resourcepackScript)

if (-not (Test-Path -LiteralPath $resourcepackZip)) {
    throw "Missing resource pack zip: $resourcepackZip"
}
if (-not (Test-Path -LiteralPath $modpackZip)) {
    throw "Missing modpack zip: $modpackZip"
}

$resourcepackSha1 = (Get-Content -Raw -Encoding UTF8 $resourcepackShaFile).Trim()
$resourcepackSha256 = (Get-Content -Raw -Encoding UTF8 $resourcepackSha256File).Trim()
$modpackSha1 = (Get-Content -Raw -Encoding UTF8 $modpackShaFile).Trim()
$modpackSha256 = (Get-Content -Raw -Encoding UTF8 $modpackSha256File).Trim()
$clientSha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $thirdpartyClientJar).Hash.ToLowerInvariant()
$clientSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $thirdpartyClientJar).Hash.ToLowerInvariant()
$commit = (git -C $ProjectRoot rev-parse --short HEAD).Trim()
$gitDirty = -not [string]::IsNullOrWhiteSpace((git -C $ProjectRoot status --short))

Write-Host "[5/8] Update release manifests"
$thirdpartyManifest = Get-Content -Raw -Encoding UTF8 $thirdpartyManifestPath | ConvertFrom-Json
$thirdpartyManifest.clientArchive.sha1 = $modpackSha1
foreach ($artifact in $thirdpartyManifest.artifacts.clientMods) {
    if ($artifact.path -eq "thirdparty/client-mods/CopiMineClient-0.1.0.jar") {
        $artifact.sha1 = $clientSha1
    }
}
($thirdpartyManifest | ConvertTo-Json -Depth 16) | Set-Content -LiteralPath $thirdpartyManifestPath -Encoding UTF8

$releaseManifest = [ordered]@{
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    gitCommit = $commit
    sourceTreeDirty = $gitDirty
    projectRoot = "copimine"
    resourcePack = [ordered]@{
        path = "resourcepacks/build/CopiMineResourcePack.zip"
        sha1 = $resourcepackSha1
        sha256 = $resourcepackSha256
        downloadUrl = $resourcePackDownloadUrl
    }
    modpack = [ordered]@{
        path = "thirdparty/CopiMineMods.zip"
        sha1 = $modpackSha1
        sha256 = $modpackSha256
        downloadUrl = $modpackDownloadUrl
    }
    clientMod = [ordered]@{
        path = "thirdparty/client-mods/CopiMineClient-0.1.0.jar"
        sha1 = $clientSha1
        sha256 = $clientSha256
    }
    database = [ordered]@{
        bundledDump = [bool](($DbDumpPath) -and (Test-Path -LiteralPath $DbDumpPath))
        dumpPathInsideArchive = "db/runtime/copimine.dump"
        note = "If no live PostgreSQL dump is bundled, the replace script keeps the existing database or restores an external dump passed as argument 2."
    }
}
($releaseManifest | ConvertTo-Json -Depth 12) | Set-Content -LiteralPath $releaseManifestPath -Encoding UTF8

$installerManifest = [ordered]@{
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    gitCommit = $commit
    sourceTreeDirty = $gitDirty
    artifacts = [ordered]@{
        resourcePack = [ordered]@{
            path = "resourcepacks/build/CopiMineResourcePack.zip"
            sha1 = $resourcepackSha1
            sha256 = $resourcepackSha256
        }
        modpack = [ordered]@{
            path = "thirdparty/CopiMineMods.zip"
            sha1 = $modpackSha1
            sha256 = $modpackSha256
        }
        clientMod = [ordered]@{
            path = "thirdparty/client-mods/CopiMineClient-0.1.0.jar"
            sha1 = $clientSha1
            sha256 = $clientSha256
        }
    }
    deploy = [ordered]@{
        scripts = [ordered]@{
            unpackAndVerify = [ordered]@{
                path = "deploy/ubuntu/copimine_unpack_and_verify.sh"
                sha256 = (Get-Sha256Lower -LiteralPath $embeddedUnpackScript)
            }
            fullReplace = [ordered]@{
                path = "deploy/ubuntu/copimine_full_replace.sh"
                sha256 = (Get-Sha256Lower -LiteralPath $embeddedReplaceScript)
            }
            sharedCommon = [ordered]@{
                path = "deploy/shared/common.sh"
                sha256 = (Get-Sha256Lower -LiteralPath $embeddedCommonScript)
            }
        }
        routes = [ordered]@{
            modpack = $modpackDownloadUrl
            resourcePack = "/resourcepacks/CopiMineResourcePack.zip"
        }
    }
    frontend = [ordered]@{
        modpackSnapshot = [ordered]@{
            path = "admin-web/frontend/assets/public-data/modpack_snapshot.json"
            sha256 = (Get-Sha256Lower -LiteralPath $modpackSnapshotPath)
        }
    }
    serverProperties = [ordered]@{
        path = "minecraft/server/server.properties"
        required = [ordered]@{
            levelSeed = "-1861153001556076901"
            resourcePack = ($resourcePackDownloadUrl -replace ':','\:')
            resourcePackSha1 = $resourcepackSha1
        }
    }
}
($installerManifest | ConvertTo-Json -Depth 16) | Set-Content -LiteralPath $installerManifestPath -Encoding UTF8

Write-Host "[6/8] Stage Linux replacement payload"
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("copimine-release-" + [guid]::NewGuid().ToString())
$payloadRoot = Join-Path $stageRoot "copimine"
New-Item -ItemType Directory -Force -Path $payloadRoot | Out-Null

$sourceDirs = Get-ChildItem -LiteralPath $ProjectRoot -Force
$excludeNames = @(".git", ".gradle", ".gradle-dist")
foreach ($entry in $sourceDirs) {
    if ($excludeNames -contains $entry.Name) {
        continue
    }
    if ($entry.Name -eq "admin-web" -and (Test-Path -LiteralPath (Join-Path $entry.FullName ".venv"))) {
        Copy-Item -LiteralPath $entry.FullName -Destination $payloadRoot -Recurse -Force
        Remove-Item -LiteralPath (Join-Path $payloadRoot "admin-web\.venv") -Recurse -Force -ErrorAction SilentlyContinue
        continue
    }
    if ($entry.Name -eq "thirdparty" -and (Test-Path -LiteralPath (Join-Path $entry.FullName "_modpack_stage"))) {
        Copy-Item -LiteralPath $entry.FullName -Destination $payloadRoot -Recurse -Force
        Remove-Item -LiteralPath (Join-Path $payloadRoot "thirdparty\_modpack_stage") -Recurse -Force -ErrorAction SilentlyContinue
        continue
    }
    Copy-Item -LiteralPath $entry.FullName -Destination $payloadRoot -Recurse -Force
}

foreach ($relative in @(
    "CopiMineClient\.gradle",
    "CopiMineClient\.gradle-dist",
    "CopiMineClient\build\tmp",
    "CopiMineClient\build\reports",
    "CopiMineClient\build\test-results",
    "thirdparty\_modpack_stage"
)) {
    $path = Join-Path $payloadRoot $relative
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$runtimeDbDir = Join-Path $payloadRoot "db\runtime"
New-Item -ItemType Directory -Force -Path $runtimeDbDir | Out-Null
$dbReadme = @(
    "CopiMine full replace archive",
    "",
    "Put a live PostgreSQL dump here as copimine.dump if you want the Ubuntu replace script",
    "to restore the database automatically from the archive.",
    "",
    "If this folder does not contain copimine.dump, the replace script keeps the existing",
    "server database unchanged by default."
) -join "`n"
Set-Content -LiteralPath (Join-Path $runtimeDbDir "README_FIRST.txt") -Value $dbReadme -Encoding UTF8
if ($DbDumpPath) {
    if (-not (Test-Path -LiteralPath $DbDumpPath)) {
        throw "DbDumpPath was provided but not found: $DbDumpPath"
    }
    Copy-Item -LiteralPath $DbDumpPath -Destination (Join-Path $runtimeDbDir "copimine.dump") -Force
}

Write-Host "[7/8] Create tar.gz release archive"
$archiveName = "copimine-opt-full-$timestamp.tar.gz"
$archivePath = Join-Path $ReleaseDir $archiveName
if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}
Push-Location $stageRoot
try {
    tar -a -cf $archivePath "copimine"
    if ($LASTEXITCODE -ne 0) {
        throw "tar failed for $archivePath"
    }
} finally {
    Pop-Location
    Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "[8/8] Finalize release metadata and validate bundle"
$deployInstallCopy = Join-Path $ReleaseDir "copimine_install.sh"
$deployUpdateCopy = Join-Path $ReleaseDir "copimine_update.sh"
$deployVerifyCopy = Join-Path $ReleaseDir "copimine_verify.sh"
Copy-Item -LiteralPath $deployInstall -Destination $deployInstallCopy -Force
Copy-Item -LiteralPath $deployUpdate -Destination $deployUpdateCopy -Force
Copy-Item -LiteralPath $deployVerify -Destination $deployVerifyCopy -Force

$archiveSha256 = Get-Sha256Lower -LiteralPath $archivePath
$archiveShaPath = "$archivePath.sha256"
Set-Content -LiteralPath $archiveShaPath -Value $archiveSha256 -Encoding ascii

$bootstrapManifestPath = [System.IO.Path]::ChangeExtension($archivePath, ".bootstrap.json")
$bootstrapManifest = [ordered]@{
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    archive = [ordered]@{
        file = [System.IO.Path]::GetFileName($archivePath)
        sha256 = $archiveSha256
        sizeBytes = (Get-Item -LiteralPath $archivePath).Length
    }
    embedded = [ordered]@{
        payloadRoot = "copimine"
        unpackAndVerify = "copimine/deploy/ubuntu/copimine_unpack_and_verify.sh"
        fullReplace = "copimine/deploy/ubuntu/copimine_full_replace.sh"
        releaseManifest = "copimine/deploy/release_manifest.json"
        installerManifest = "copimine/deploy/installer_manifest.json"
    }
    exportedHelpers = [ordered]@{
        install = [System.IO.Path]::GetFileName($deployInstallCopy)
        update = [System.IO.Path]::GetFileName($deployUpdateCopy)
        verify = [System.IO.Path]::GetFileName($deployVerifyCopy)
    }
}
($bootstrapManifest | ConvertTo-Json -Depth 12) | Set-Content -LiteralPath $bootstrapManifestPath -Encoding UTF8

try {
    Invoke-Checked -FilePath "powershell" -Arguments @(
        "-ExecutionPolicy", "Bypass",
        "-File", $validateReleaseScript,
        "-ProjectRoot", $ProjectRoot,
        "-ArchivePath", $archivePath,
        "-BootstrapManifestPath", $bootstrapManifestPath
    )
} catch {
    Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $archiveShaPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $bootstrapManifestPath -Force -ErrorAction SilentlyContinue
    throw
}

Write-Host ""
Write-Host "RELEASE READY"
Write-Host "Archive: $archivePath"
Write-Host "Archive SHA256: $archiveSha256"
Write-Host "Deploy install: $deployInstallCopy"
Write-Host "Deploy update: $deployUpdateCopy"
Write-Host "Deploy verify: $deployVerifyCopy"
Write-Host "Bootstrap manifest: $bootstrapManifestPath"
Write-Host "Git commit: $commit"
Write-Host "Source tree dirty: $gitDirty"
Write-Host "Resource pack SHA1: $resourcepackSha1"
Write-Host "Modpack SHA1: $modpackSha1"
