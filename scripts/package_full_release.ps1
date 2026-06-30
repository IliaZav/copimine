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
$modpackZip = Join-Path $ProjectRoot "thirdparty\CopiMineMods.zip"
$modpackShaFile = Join-Path $ProjectRoot "thirdparty\CopiMineMods.sha1"
$thirdpartyManifestPath = Join-Path $ProjectRoot "thirdparty\thirdparty_manifest.json"
$releaseManifestPath = Join-Path $ProjectRoot "deploy\release_manifest.json"
$deployScript = Join-Path $ProjectRoot "deploy\ubuntu\copimine_full_replace.sh"

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
    Set-Content -LiteralPath (Join-Path $ProjectRoot "thirdparty\checksums.txt") -Value ($lines -join "`n") -Encoding UTF8
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
$modpackSha1 = (Get-Content -Raw -Encoding UTF8 $modpackShaFile).Trim()
$clientSha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $thirdpartyClientJar).Hash.ToLowerInvariant()
$commit = (git -C $ProjectRoot rev-parse --short HEAD).Trim()

Write-Host "[5/8] Update third-party manifest and release manifest"
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
    projectRoot = "copimine"
    resourcePack = [ordered]@{
        path = "resourcepacks/build/CopiMineResourcePack.zip"
        sha1 = $resourcepackSha1
        downloadUrl = "http://admin.copimine.ru:18080/resourcepacks/CopiMineResourcePack.zip"
    }
    modpack = [ordered]@{
        path = "thirdparty/CopiMineMods.zip"
        sha1 = $modpackSha1
        downloadUrl = "/downloads/CopiMineMods.zip"
    }
    clientMod = [ordered]@{
        path = "thirdparty/client-mods/CopiMineClient-0.1.0.jar"
        sha1 = $clientSha1
    }
    database = [ordered]@{
        bundledDump = [bool](($DbDumpPath) -and (Test-Path -LiteralPath $DbDumpPath))
        dumpPathInsideArchive = "db/runtime/copimine.dump"
        note = "If no live PostgreSQL dump is bundled, the replace script keeps the existing database or restores an external dump passed as argument 2."
    }
}
($releaseManifest | ConvertTo-Json -Depth 12) | Set-Content -LiteralPath $releaseManifestPath -Encoding UTF8

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

Write-Host "[8/8] Export deploy helper"
$deployCopy = Join-Path $ReleaseDir "copimine_full_replace.sh"
Copy-Item -LiteralPath $deployScript -Destination $deployCopy -Force

Write-Host ""
Write-Host "RELEASE READY"
Write-Host "Archive: $archivePath"
Write-Host "Deploy script: $deployCopy"
Write-Host "Git commit: $commit"
Write-Host "Resource pack SHA1: $resourcepackSha1"
Write-Host "Modpack SHA1: $modpackSha1"
