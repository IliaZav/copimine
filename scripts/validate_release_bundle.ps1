param(
    [string]$ProjectRoot = "",
    [string]$ArchivePath = "",
    [string]$BootstrapManifestPath = ""
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
}
$ProjectRoot = (Resolve-Path $ProjectRoot).Path

if (-not $ArchivePath) {
    $releaseDir = Join-Path (Split-Path (Split-Path $ProjectRoot -Parent) -Parent) "release"
    $ArchivePath = Get-ChildItem -LiteralPath $releaseDir -Filter "copimine-opt-full-*.tar.gz" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not $ArchivePath -or -not (Test-Path -LiteralPath $ArchivePath)) {
    throw "Release archive not found."
}

$ArchivePath = (Resolve-Path $ArchivePath).Path
$ArchiveShaSidecar = "$ArchivePath.sha256"
if (-not $BootstrapManifestPath) {
    $BootstrapManifestPath = [System.IO.Path]::ChangeExtension($ArchivePath, ".bootstrap.json")
}

if (-not (Test-Path -LiteralPath $ArchiveShaSidecar)) {
    throw "Missing archive SHA256 sidecar: $ArchiveShaSidecar"
}
if (-not (Test-Path -LiteralPath $BootstrapManifestPath)) {
    throw "Missing bootstrap manifest: $BootstrapManifestPath"
}

function Get-Utf8Trimmed([string]$Path) {
    return ([System.IO.File]::ReadAllText($Path, [System.Text.UTF8Encoding]::new($false))).Trim()
}

function Get-Sha1([string]$Path) {
    return (Get-FileHash -Algorithm SHA1 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Require-Equal([string]$Expected, [string]$Actual, [string]$Message, [System.Collections.Generic.List[string]]$Errors) {
    if ($Expected -ne $Actual) {
        $Errors.Add("$Message Expected=$Expected Actual=$Actual")
    }
}

$errors = [System.Collections.Generic.List[string]]::new()
$archiveShaExpected = (Get-Utf8Trimmed $ArchiveShaSidecar).ToLowerInvariant()
$archiveShaActual = Get-Sha256 $ArchivePath
Require-Equal $archiveShaExpected $archiveShaActual "Archive SHA256 mismatch." $errors

$bootstrap = Get-Content -LiteralPath $BootstrapManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
Require-Equal ([System.IO.Path]::GetFileName($ArchivePath)) ([string]$bootstrap.archive.file) "bootstrap_manifest archive filename mismatch." $errors
Require-Equal $archiveShaActual ([string]$bootstrap.archive.sha256) "bootstrap_manifest archive SHA256 mismatch." $errors

$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("copimine-release-validate-" + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null

try {
    tar -xzf $ArchivePath -C $tmpRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract archive: $ArchivePath"
    }

    $payloadRoot = Join-Path $tmpRoot "copimine"
    if (-not (Test-Path -LiteralPath $payloadRoot)) {
        throw "Archive payload root 'copimine' not found after extraction."
    }

    $releaseManifestPath = Join-Path $payloadRoot "deploy\release_manifest.json"
    $installerManifestPath = Join-Path $payloadRoot "deploy\installer_manifest.json"
    $modpackZip = Join-Path $payloadRoot "thirdparty\CopiMineMods.zip"
    $modpackSha1Path = Join-Path $payloadRoot "thirdparty\CopiMineMods.sha1"
    $modpackSha256Path = Join-Path $payloadRoot "thirdparty\CopiMineMods.sha256"
    $resourcePackZip = Join-Path $payloadRoot "resourcepacks\build\CopiMineResourcePack.zip"
    $resourcePackSha1Path = Join-Path $payloadRoot "resourcepacks\build\CopiMineResourcePack.sha1"
    $resourcePackSha256Path = Join-Path $payloadRoot "resourcepacks\build\CopiMineResourcePack.sha256"
    $clientJar = Join-Path $payloadRoot "thirdparty\client-mods\CopiMineClient-0.1.0.jar"
    $snapshotPath = Join-Path $payloadRoot "admin-web\frontend\assets\public-data\modpack_snapshot.json"
    $serverPropertiesPath = Join-Path $payloadRoot "minecraft\server\server.properties"
    $backendMainPath = Join-Path $payloadRoot "admin-web\backend\main.py"
    $unpackScriptPath = Join-Path $payloadRoot "deploy\ubuntu\copimine_unpack_and_verify.sh"
    $replaceScriptPath = Join-Path $payloadRoot "deploy\ubuntu\copimine_full_replace.sh"
    $commonScriptPath = Join-Path $payloadRoot "deploy\shared\common.sh"
    $forbiddenPayloadPaths = @(
        "admin-web\.env",
        "admin-web\.venv",
        "admin-web\data",
        "admin-web\backups",
        "minecraft\server\logs",
        "minecraft\server\cache",
        "minecraft\server\libraries",
        "minecraft\server\CopiMine",
        "minecraft\server\CopiMine_nether",
        "minecraft\server\CopiMine_the_end",
        "minecraft\server\world",
        "minecraft\server\world_nether",
        "minecraft\server\world_the_end",
        "minecraft\server\world_test",
        "minecraft\server\world_test_nether",
        "minecraft\server\world_test_the_end",
        "minecraft\server\worldTestCP",
        "minecraft\server\worldTestCP_nether",
        "minecraft\server\worldTestCP_the_end",
        "minecraft\server\ops.json",
        "minecraft\server\usercache.json",
        "minecraft\server\whitelist.json",
        "minecraft\server\banned-players.json",
        "minecraft\server\banned-ips.json",
        "minecraft\server\plugins\.paper-remapped",
        "minecraft\server\plugins\TAB\anti-override.log",
        "minecraft\server\plugins\TAB\playerdata.yml",
        "minecraft\server\plugins\TAB\skincache.yml",
        "minecraft\server\plugins\TAB\users.yml"
    )

    foreach ($required in @(
        $releaseManifestPath,
        $installerManifestPath,
        $modpackZip,
        $modpackSha1Path,
        $modpackSha256Path,
        $resourcePackZip,
        $resourcePackSha1Path,
        $resourcePackSha256Path,
        $clientJar,
        $snapshotPath,
        $serverPropertiesPath,
        $backendMainPath,
        $unpackScriptPath,
        $replaceScriptPath,
        $commonScriptPath
    )) {
        if (-not (Test-Path -LiteralPath $required)) {
            $errors.Add("Missing file in archive: $required")
        }
    }

    foreach ($relative in $forbiddenPayloadPaths) {
        $forbiddenPath = Join-Path $payloadRoot $relative
        if (Test-Path -LiteralPath $forbiddenPath) {
            $errors.Add("Runtime-only file must not be bundled in release archive: $relative")
        }
    }

    $forbiddenWorldDirs = Get-ChildItem -LiteralPath (Join-Path $payloadRoot "minecraft\server") -Force -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(world|world_|CopiMine|CopiMine_|paper-world)' }
    foreach ($worldDir in $forbiddenWorldDirs) {
        $errors.Add("Runtime world directory must not be bundled in release archive: minecraft/server/$($worldDir.Name)")
    }

    if ($errors.Count -eq 0) {
        $releaseManifest = Get-Content -LiteralPath $releaseManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $installerManifest = Get-Content -LiteralPath $installerManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $snapshot = Get-Content -LiteralPath $snapshotPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $backendMain = Get-Content -LiteralPath $backendMainPath -Raw -Encoding UTF8
        $serverProperties = Get-Content -LiteralPath $serverPropertiesPath -Raw -Encoding UTF8
        $unpackScript = Get-Content -LiteralPath $unpackScriptPath -Raw -Encoding UTF8
        $replaceScript = Get-Content -LiteralPath $replaceScriptPath -Raw -Encoding UTF8

        $modpackSha1 = Get-Sha1 $modpackZip
        $modpackSha256 = Get-Sha256 $modpackZip
        $resourcePackSha1 = Get-Sha1 $resourcePackZip
        $resourcePackSha256 = Get-Sha256 $resourcePackZip
        $clientSha1 = Get-Sha1 $clientJar
        $clientSha256 = Get-Sha256 $clientJar

        Require-Equal $modpackSha1 (Get-Utf8Trimmed $modpackSha1Path).ToLowerInvariant() "Modpack SHA1 sidecar mismatch." $errors
        Require-Equal $modpackSha256 (Get-Utf8Trimmed $modpackSha256Path).ToLowerInvariant() "Modpack SHA256 sidecar mismatch." $errors
        Require-Equal $resourcePackSha1 (Get-Utf8Trimmed $resourcePackSha1Path).ToLowerInvariant() "Resource pack SHA1 sidecar mismatch." $errors
        Require-Equal $resourcePackSha256 (Get-Utf8Trimmed $resourcePackSha256Path).ToLowerInvariant() "Resource pack SHA256 sidecar mismatch." $errors

        Require-Equal $modpackSha1 ([string]$releaseManifest.modpack.sha1) "release_manifest modpack.sha1 mismatch." $errors
        Require-Equal $modpackSha256 ([string]$releaseManifest.modpack.sha256) "release_manifest modpack.sha256 mismatch." $errors
        Require-Equal $resourcePackSha1 ([string]$releaseManifest.resourcePack.sha1) "release_manifest resourcePack.sha1 mismatch." $errors
        Require-Equal $resourcePackSha256 ([string]$releaseManifest.resourcePack.sha256) "release_manifest resourcePack.sha256 mismatch." $errors
        Require-Equal $clientSha1 ([string]$releaseManifest.clientMod.sha1) "release_manifest clientMod.sha1 mismatch." $errors

        Require-Equal $modpackSha1 ([string]$snapshot.sha1) "modpack_snapshot sha1 mismatch." $errors
        Require-Equal $modpackSha256 ([string]$snapshot.sha256) "modpack_snapshot sha256 mismatch." $errors
        Require-Equal "/downloads/CopiMineMods.zip" ([string]$snapshot.downloadUrl) "modpack_snapshot downloadUrl mismatch." $errors

        Require-Equal $modpackSha1 ([string]$installerManifest.artifacts.modpack.sha1) "installer_manifest modpack.sha1 mismatch." $errors
        Require-Equal $modpackSha256 ([string]$installerManifest.artifacts.modpack.sha256) "installer_manifest modpack.sha256 mismatch." $errors
        Require-Equal $resourcePackSha1 ([string]$installerManifest.artifacts.resourcePack.sha1) "installer_manifest resourcePack.sha1 mismatch." $errors
        Require-Equal $resourcePackSha256 ([string]$installerManifest.artifacts.resourcePack.sha256) "installer_manifest resourcePack.sha256 mismatch." $errors
        Require-Equal $clientSha1 ([string]$installerManifest.artifacts.clientMod.sha1) "installer_manifest clientMod.sha1 mismatch." $errors
        Require-Equal $clientSha256 ([string]$installerManifest.artifacts.clientMod.sha256) "installer_manifest clientMod.sha256 mismatch." $errors

        $expectedUnpackScriptSha = [string]$installerManifest.deploy.scripts.unpackAndVerify.sha256
        $expectedReplaceScriptSha = [string]$installerManifest.deploy.scripts.fullReplace.sha256
        $expectedCommonScriptSha = [string]$installerManifest.deploy.scripts.sharedCommon.sha256
        Require-Equal (Get-Sha256 $unpackScriptPath) $expectedUnpackScriptSha "installer_manifest unpack script SHA256 mismatch." $errors
        Require-Equal (Get-Sha256 $replaceScriptPath) $expectedReplaceScriptSha "installer_manifest full replace script SHA256 mismatch." $errors
        Require-Equal (Get-Sha256 $commonScriptPath) $expectedCommonScriptSha "installer_manifest shared common script SHA256 mismatch." $errors

        if ($serverProperties -notmatch ("(?m)^resource-pack-sha1=" + [regex]::Escape($resourcePackSha1) + "\r?$")) {
            $errors.Add("server.properties resource-pack-sha1 does not match actual resource pack SHA1.")
        }
        if ($serverProperties -notmatch "(?m)^level-seed=-1861153001556076901\r?$") {
            $errors.Add("server.properties level-seed mismatch.")
        }

        if ($backendMain -notmatch '@app\.get\("/downloads/CopiMineMods\.zip"\)') {
            $errors.Add("Backend route for /downloads/CopiMineMods.zip is missing.")
        }
        if ($backendMain -notmatch '@app\.get\("/resourcepacks/CopiMineResourcePack\.zip"\)') {
            $errors.Add("Backend route for /resourcepacks/CopiMineResourcePack.zip is missing.")
        }

        $unpackPreserveBlock = [regex]::Match($unpackScript, 'PRESERVE_PATHS=\((?<body>.*?)\n\)', 'Singleline').Groups['body'].Value
        $replaceRuntimeBlock = [regex]::Match($replaceScript, 'RUNTIME_PATHS=\((?<body>.*?)\n\)', 'Singleline').Groups['body'].Value

        if ($unpackPreserveBlock -match 'thirdparty/CopiMineMods\.sha1' -or $replaceRuntimeBlock -match 'thirdparty/CopiMineMods\.sha1') {
            $errors.Add("Deploy scripts still preserve modpack SHA sidecars from old runtime.")
        }
        if ($unpackPreserveBlock -match 'thirdparty/CopiMineMods\.sha256' -or $replaceRuntimeBlock -match 'thirdparty/CopiMineMods\.sha256') {
            $errors.Add("Deploy scripts still preserve modpack SHA256 sidecars from old runtime.")
        }
        if ($unpackPreserveBlock -match 'thirdparty/modpack_manifest\.json' -or $replaceRuntimeBlock -match 'thirdparty/modpack_manifest\.json') {
            $errors.Add("Deploy scripts still preserve modpack manifest from old runtime.")
        }
        if ($replaceRuntimeBlock -match 'deploy/runtime_metadata\.json') {
            $errors.Add("Full replace script still preserves old runtime metadata.")
        }
        if ($replaceRuntimeBlock -match 'thirdparty/CopiMineMods\.zip') {
            $errors.Add("Full replace script still preserves old CopiMineMods.zip from runtime state.")
        }
        if ($replaceRuntimeBlock -match 'resourcepacks/build') {
            $errors.Add("Full replace script still preserves old resourcepacks/build from runtime state.")
        }
    }
}
finally {
    Remove-Item -LiteralPath $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue
}

if ($errors.Count -gt 0) {
    throw ("Release validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host "Release bundle validation passed."
