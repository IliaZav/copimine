param(
    [string]$ProjectRoot = "",
    [string]$ReleaseDir = "",
    [string]$DbDumpPath = "",
    [string]$ResourcePackDownloadUrl = ""
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
}
$ProjectRoot = (Resolve-Path $ProjectRoot).Path

function Resolve-GitRoot {
    param([Parameter(Mandatory = $true)][string]$StartPath)
    $current = Resolve-Path $StartPath
    while ($current) {
        if (Test-Path -LiteralPath (Join-Path $current.Path ".git")) {
            return $current.Path
        }
        $parent = Split-Path $current.Path -Parent
        if (-not $parent -or $parent -eq $current.Path) {
            break
        }
        $current = Resolve-Path $parent
    }
    throw "Git root not found above $StartPath"
}

$gitRoot = Resolve-GitRoot -StartPath $ProjectRoot
$sourceCommitBeforeBuild = (git -C $gitRoot rev-parse --short HEAD).Trim()
$sourceTreeDirtyBeforeBuild = -not [string]::IsNullOrWhiteSpace((git -C $gitRoot status --short --untracked-files=no))
if ($sourceTreeDirtyBeforeBuild) {
    # Windows may materialize tracked text files as CRLF even though the Git
    # blob is clean. Permit that line-ending-only difference; all real content
    # changes still require an explicit commit before packaging.
    git -C $gitRoot diff --quiet --ignore-space-at-eol --exit-code -- .
    if ($LASTEXITCODE -ne 0) {
        throw 'Release packaging requires committed tracked changes. Commit or stash them before packaging.'
    }
    $sourceTreeDirtyBeforeBuild = $false
}

if (-not $ReleaseDir) {
    $workspaceRoot = Split-Path (Split-Path $ProjectRoot -Parent) -Parent
    $ReleaseDir = Join-Path $workspaceRoot "release"
}
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$ReleaseDir = (Resolve-Path -LiteralPath $ReleaseDir).Path

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
$deployUnpack = Join-Path $ProjectRoot "deploy\ubuntu\copimine_unpack_and_verify.sh"
$deployReplace = Join-Path $ProjectRoot "deploy\ubuntu\copimine_full_replace.sh"
$deployCommon = Join-Path $ProjectRoot "deploy\shared\common.sh"
$deployGameHardening = Join-Path $ProjectRoot "deploy\ubuntu\apply_game_hardening.sh"
$gameHardeningRuntime = Join-Path $ProjectRoot "deploy\shared\harden_game_runtime.py"
$gameHardeningPolicy = Join-Path $ProjectRoot "deploy\templates\game-runtime-hardening.json"
$voicechatTemplate = Join-Path $ProjectRoot "deploy\templates\voicechat-server.properties"
$gameHardeningService = Join-Path $ProjectRoot "admin-web\deploy\copimine-game-hardening.service"
$uploadScript = Join-Path $ProjectRoot "scripts\windows\upload_release.ps1"
$validateReleaseScript = Join-Path $ProjectRoot "scripts\validate_release_bundle.ps1"
$embeddedUnpackScript = Join-Path $ProjectRoot "deploy\ubuntu\copimine_unpack_and_verify.sh"
$embeddedReplaceScript = Join-Path $ProjectRoot "deploy\ubuntu\copimine_full_replace.sh"
$embeddedCommonScript = Join-Path $ProjectRoot "deploy\shared\common.sh"
$serverPropertiesPath = Join-Path $ProjectRoot "minecraft\server\server.properties"
$resourcePackDownloadUrl = if ($ResourcePackDownloadUrl) {
    $ResourcePackDownloadUrl
} else {
    "http://copimine.ru:18080/resourcepacks/CopiMineResourcePack.zip"
}
$modpackDownloadUrl = "/downloads/CopiMineMods.zip"
if ($resourcePackDownloadUrl -notmatch '^https?://[^/]+(?:/.*)?$') {
    throw 'ResourcePackDownloadUrl must be an absolute http:// or https:// URL.'
}

function Write-Utf8NoBomFile {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($LiteralPath, $Content + [Environment]::NewLine, $utf8NoBom)
}

function Update-ServerProperties {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$ResourcePackUrl,
        [Parameter(Mandatory = $true)][string]$ResourcePackSha1
    )
    if (-not (Test-Path -LiteralPath $LiteralPath)) {
        throw "Missing server.properties template: $LiteralPath"
    }
    $updates = [ordered]@{
        'level-seed' = '-1861153001556076901'
        'resource-pack' = ($ResourcePackUrl -replace ':', '\:')
        'resource-pack-sha1' = $ResourcePackSha1
    }
    $seen = @{}
    $result = foreach ($line in Get-Content -LiteralPath $LiteralPath -Encoding UTF8) {
        if ($line.StartsWith('#') -or $line -notmatch '=') {
            $line
            continue
        }
        $key = $line.Split('=', 2)[0]
        if ($updates.Contains($key)) {
            $seen[$key] = $true
            "$key=$($updates[$key])"
        } else {
            $line
        }
    }
    foreach ($key in $updates.Keys) {
        if (-not $seen.ContainsKey($key)) { $result += "$key=$($updates[$key])" }
    }
    Write-Utf8NoBomFile -LiteralPath $LiteralPath -Content ($result -join "`n")
}

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
    $hashes = @{}
    $lines = foreach ($relative in $entries) {
        $full = Join-Path $ProjectRoot $relative
        if (-not (Test-Path -LiteralPath $full)) {
            throw "Missing checksum file: $relative"
        }
        $sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $full).Hash.ToLowerInvariant()
        $normalized = $relative -replace '\\','/'
        $hashes[$normalized] = $sha256
        "SHA256  $normalized  $sha256"
    }
    Set-Content -LiteralPath (Join-Path $ProjectRoot "thirdparty\checksums.txt") -Value ($lines -join "`n") -Encoding ascii
    return $hashes
}

function Remove-PayloadPath {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    $path = Join-Path $payloadRoot $RelativePath
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "[1/8] Build CopiMineClient"
Invoke-Checked -FilePath "powershell" -Arguments @("-ExecutionPolicy", "Bypass", "-File", $clientBuildScript)
if (-not (Test-Path -LiteralPath $clientJar)) {
    throw "Missing built client jar: $clientJar"
}

Write-Host "[2/8] Sync client jar into site modpack inputs"
Copy-Item -LiteralPath $clientJar -Destination $thirdpartyClientJar -Force
$thirdpartySha256 = Write-Checksums

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
$serverPropertiesPath = Join-Path $ProjectRoot 'minecraft\server\server.properties'
Update-ServerProperties -LiteralPath $serverPropertiesPath -ResourcePackUrl $resourcePackDownloadUrl -ResourcePackSha1 $resourcepackSha1
$commit = $sourceCommitBeforeBuild
$gitDirty = $sourceTreeDirtyBeforeBuild

Write-Host "[5/8] Update release manifests"
$thirdpartyManifest = Get-Content -Raw -Encoding UTF8 $thirdpartyManifestPath | ConvertFrom-Json
$thirdpartyManifest.clientArchive.sha1 = $modpackSha1
$thirdpartyManifest.clientArchive | Add-Member -NotePropertyName sha256 -NotePropertyValue $modpackSha256 -Force
foreach ($artifact in $thirdpartyManifest.artifacts.clientMods) {
    $relative = ([string]$artifact.path).Replace('\\', '/')
    if ($thirdpartySha256.ContainsKey($relative)) {
        $artifact | Add-Member -NotePropertyName sha256 -NotePropertyValue $thirdpartySha256[$relative] -Force
    }
    if ($artifact.path -eq "thirdparty/client-mods/CopiMineClient-0.1.0.jar") {
        $artifact.sha1 = $clientSha1
        $artifact.sha256 = $clientSha256
    }
}
foreach ($artifact in $thirdpartyManifest.artifacts.serverPlugins) {
    $relative = ([string]$artifact.path).Replace('\\', '/')
    if ($thirdpartySha256.ContainsKey($relative)) {
        $artifact | Add-Member -NotePropertyName sha256 -NotePropertyValue $thirdpartySha256[$relative] -Force
    }
}
Write-Utf8NoBomFile -LiteralPath $thirdpartyManifestPath -Content ($thirdpartyManifest | ConvertTo-Json -Depth 16)

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
Write-Utf8NoBomFile -LiteralPath $releaseManifestPath -Content ($releaseManifest | ConvertTo-Json -Depth 12)

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
            gameRuntimeHardening = [ordered]@{
                applyScript = [ordered]@{
                    path = "deploy/ubuntu/apply_game_hardening.sh"
                    sha256 = (Get-Sha256Lower -LiteralPath $deployGameHardening)
                }
                runtimeScript = [ordered]@{
                    path = "deploy/shared/harden_game_runtime.py"
                    sha256 = (Get-Sha256Lower -LiteralPath $gameHardeningRuntime)
                }
                policy = [ordered]@{
                    path = "deploy/templates/game-runtime-hardening.json"
                    sha256 = (Get-Sha256Lower -LiteralPath $gameHardeningPolicy)
                }
                voicechatTemplate = [ordered]@{
                    path = "deploy/templates/voicechat-server.properties"
                    sha256 = (Get-Sha256Lower -LiteralPath $voicechatTemplate)
                }
                systemdUnit = [ordered]@{
                    path = "admin-web/deploy/copimine-game-hardening.service"
                    sha256 = (Get-Sha256Lower -LiteralPath $gameHardeningService)
                }
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
Write-Utf8NoBomFile -LiteralPath $installerManifestPath -Content ($installerManifest | ConvertTo-Json -Depth 16)

Write-Host "[6/8] Stage Linux replacement payload"
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("copimine-release-" + [guid]::NewGuid().ToString())
$payloadRoot = Join-Path $stageRoot "copimine"
$trackedTreeTar = Join-Path $stageRoot 'tracked-tree.tar'
New-Item -ItemType Directory -Force -Path $stageRoot | Out-Null
& git -C $ProjectRoot archive --format=tar --prefix=copimine/ HEAD -o $trackedTreeTar
if ($LASTEXITCODE -ne 0) {
    throw 'git archive failed while staging the tracked release tree.'
}
tar -xf $trackedTreeTar -C $stageRoot
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to extract the tracked release tree into the staging directory.'
}
Remove-Item -LiteralPath $trackedTreeTar -Force

# Only these generated artifacts are permitted to enter a release archive.  This
# prevents ignored runtime data, local secrets and ad-hoc downloads from being
# copied just because they exist in the developer checkout.
$generatedReleaseFiles = @(
    'admin-web\frontend\assets\public-data\modpack_snapshot.json',
    'deploy\installer_manifest.json',
    'deploy\release_manifest.json',
    'minecraft\server\server.properties',
    'resourcepacks\build\CopiMineResourcePack.zip',
    'resourcepacks\build\CopiMineResourcePack.sha1',
    'resourcepacks\build\CopiMineResourcePack.sha256',
    'thirdparty\CopiMineMods.zip',
    'thirdparty\CopiMineMods.sha1',
    'thirdparty\CopiMineMods.sha256',
    'thirdparty\client-mods\CopiMineClient-0.1.0.jar',
    'thirdparty\checksums.txt',
    'thirdparty\thirdparty_manifest.json'
)
$serverReleaseJars = @(
    'minecraft\server\purpur.jar',
    'minecraft\server\plugins\AuthEffects.jar',
    'minecraft\server\plugins\AuthMe-5.6.0.jar',
    'minecraft\server\plugins\Chunky-Bukkit-1.4.40.jar',
    'minecraft\server\plugins\CopiMineArtifacts.jar',
    'minecraft\server\plugins\CopiMineEconomyCore.jar',
    'minecraft\server\plugins\CopiMineElectionCore.jar',
    'minecraft\server\plugins\CopiMineNarcotics.jar',
    'minecraft\server\plugins\CopiMineUltimateAdminPlus.jar',
    'minecraft\server\plugins\CopiMineWorldCore.jar',
    'minecraft\server\plugins\CoreProtect-CE-23.0.jar',
    'minecraft\server\plugins\emotecraft-2.4.12-bukkit.jar',
    'minecraft\server\plugins\EntityClearer.jar',
    'minecraft\server\plugins\EssentialsX-2.21.2.jar',
    'minecraft\server\plugins\EssentialsXChat-2.21.2.jar',
    'minecraft\server\plugins\EssentialsXSpawn-2.21.2.jar',
    'minecraft\server\plugins\FarmControl-1.3.0.jar',
    'minecraft\server\plugins\GrimAC.jar',
    'minecraft\server\plugins\GSit.jar',
    'minecraft\server\plugins\ImageFrame.jar',
    'minecraft\server\plugins\LuckPerms-Bukkit-5.5.42.jar',
    'minecraft\server\plugins\PlaceholderAPI-2.12.2.jar',
    'minecraft\server\plugins\ProtocolLib.jar',
    'minecraft\server\plugins\SeeMore-1.0.2.jar',
    'minecraft\server\plugins\TAB.v6.0.1.jar',
    'minecraft\server\plugins\Vault.jar',
    'minecraft\server\plugins\voicechat-bukkit-2.6.11.jar',
    'minecraft\server\plugins\worldedit-bukkit-7.3.9.jar',
    'minecraft\server\plugins\worldguard-bukkit-7.0.12-dist.jar'
)
foreach ($relative in ($generatedReleaseFiles + $serverReleaseJars)) {
    $source = Join-Path $ProjectRoot $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Required release artifact is missing: $relative"
    }
    $destination = Join-Path $payloadRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Force
}

# The Windows tar implementation may materialize text blobs as CRLF while
# staging. Ubuntu executes these files directly, so normalize every shell
# script in the payload to LF before creating the archive.
$shellEncoding = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem -LiteralPath $payloadRoot -Recurse -File -Filter '*.sh' | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    $text = $text -replace "`r`n", "`n" -replace "`r", "`n"
    [System.IO.File]::WriteAllText($_.FullName, $text, $shellEncoding)
}

# Git archives use the repository blob bytes (LF on this project), while the
# Windows checkout may have CRLF files. Recalculate embedded deploy hashes from
# the staged payload so the manifest validates on Ubuntu exactly as extracted.
$installerManifest.deploy.scripts.unpackAndVerify.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\ubuntu\copimine_unpack_and_verify.sh')
$installerManifest.deploy.scripts.fullReplace.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\ubuntu\copimine_full_replace.sh')
$installerManifest.deploy.scripts.sharedCommon.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\shared\common.sh')
$installerManifest.deploy.scripts.gameRuntimeHardening.applyScript.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\ubuntu\apply_game_hardening.sh')
$installerManifest.deploy.scripts.gameRuntimeHardening.runtimeScript.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\shared\harden_game_runtime.py')
$installerManifest.deploy.scripts.gameRuntimeHardening.policy.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\templates\game-runtime-hardening.json')
$installerManifest.deploy.scripts.gameRuntimeHardening.voicechatTemplate.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'deploy\templates\voicechat-server.properties')
$installerManifest.deploy.scripts.gameRuntimeHardening.systemdUnit.sha256 = Get-Sha256Lower -LiteralPath (Join-Path $payloadRoot 'admin-web\deploy\copimine-game-hardening.service')
$installerManifestJson = $installerManifest | ConvertTo-Json -Depth 16
Write-Utf8NoBomFile -LiteralPath $installerManifestPath -Content $installerManifestJson
Write-Utf8NoBomFile -LiteralPath (Join-Path $payloadRoot 'deploy\installer_manifest.json') -Content $installerManifestJson

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
$deployUnpackCopy = Join-Path $ReleaseDir "copimine_unpack_and_verify.sh"
$deployReplaceCopy = Join-Path $ReleaseDir "copimine_full_replace.sh"
$deployCommonCopy = Join-Path $ReleaseDir "copimine_common.sh"
$uploadScriptCopy = Join-Path $ReleaseDir "upload_release.ps1"
Copy-Item -LiteralPath $deployInstall -Destination $deployInstallCopy -Force
Copy-Item -LiteralPath $deployUpdate -Destination $deployUpdateCopy -Force
Copy-Item -LiteralPath $deployVerify -Destination $deployVerifyCopy -Force
Copy-Item -LiteralPath $deployUnpack -Destination $deployUnpackCopy -Force
Copy-Item -LiteralPath $deployReplace -Destination $deployReplaceCopy -Force
Copy-Item -LiteralPath $deployCommon -Destination $deployCommonCopy -Force
Copy-Item -LiteralPath $uploadScript -Destination $uploadScriptCopy -Force

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
        unpackAndVerify = [System.IO.Path]::GetFileName($deployUnpackCopy)
        fullReplace = [System.IO.Path]::GetFileName($deployReplaceCopy)
        sharedCommon = [System.IO.Path]::GetFileName($deployCommonCopy)
        uploadWindows = [System.IO.Path]::GetFileName($uploadScriptCopy)
    }
}
Write-Utf8NoBomFile -LiteralPath $bootstrapManifestPath -Content ($bootstrapManifest | ConvertTo-Json -Depth 12)

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
Write-Host "Deploy unpack: $deployUnpackCopy"
Write-Host "Deploy replace: $deployReplaceCopy"
Write-Host "Deploy common: $deployCommonCopy"
Write-Host "Upload script: $uploadScriptCopy"
Write-Host "Bootstrap manifest: $bootstrapManifestPath"
Write-Host "Git commit: $commit"
Write-Host "Source tree dirty: $gitDirty"
Write-Host "Resource pack SHA1: $resourcepackSha1"
Write-Host "Modpack SHA1: $modpackSha1"
