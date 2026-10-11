[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BuildJavaHome,
    [Parameter(Mandatory)][string]$GrimPatchJavaHome,
    [string]$PythonExe = 'python'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$profile = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$pluginLock = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/server-plugins.lock.json') -Raw | ConvertFrom-Json
$clientModsLock = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/client-mods.lock.json') -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'DownloadPinnedArtifact.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-LockedJavaMajor([string]$javaHome) {
    $java = Join-Path $javaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw ('Java home is missing java.exe: ' + $javaHome) }
    $version = (& $java --version | Out-String)
    if ($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(?:openjdk|java)(?: version)?[ "]+(?<major>\d+)') {
        throw ('Cannot read Java version from: ' + $javaHome)
    }
    return [int]$Matches.major
}

if ((Get-LockedJavaMajor $BuildJavaHome) -ne $profile.buildJavaMajor) {
    throw ('Minecraft 26.3 migration artifacts require Java ' + $profile.buildJavaMajor)
}
if ((Get-LockedJavaMajor $GrimPatchJavaHome) -ne $profile.grimPatchJavaMajor) {
    throw ('The Grim compatibility patch requires Java ' + $profile.grimPatchJavaMajor)
}

$paper = $profile.paper
$paperFilename = ('paper-' + $profile.minecraftVersion + '-' + $paper.build + '.jar')
if ($paper.build -le 0 -or $paper.channel -notin @('BETA', 'STABLE') -or
    $paper.sha256 -notmatch '^[0-9a-f]{64}$' -or [long]$paper.size -le 0 -or
    [long]$paper.size -gt 500000000) {
    throw 'Pinned Paper server metadata is invalid'
}
$paperUri = $null
if (-not [Uri]::TryCreate([string]$paper.url, [UriKind]::Absolute, [ref]$paperUri) -or
    $paperUri.Scheme -ne 'https' -or $paperUri.Host -ne 'fill-data.papermc.io' -or
    $paperUri.AbsolutePath -cne ('/v1/objects/' + $paper.sha256 + '/' + $paperFilename)) {
    throw 'Pinned Paper server URL does not match its locked SHA-256 and build'
}
$paperServerDirectory = Join-Path $root 'build/minecraft-26.3/server'
$paperServerCandidate = Join-Path $paperServerDirectory $paperFilename
Assert-AtomicFileDestination -Path $paperServerCandidate -Description 'Paper server candidate' -StopAt $root
$paperCandidateValid = $false
if (Test-Path -LiteralPath $paperServerCandidate -PathType Leaf) {
    if ((Get-Item -LiteralPath $paperServerCandidate).Length -eq [long]$paper.size) {
        $paperCandidateValid = (Get-FileHash -LiteralPath $paperServerCandidate -Algorithm SHA256).Hash.ToLowerInvariant() -eq $paper.sha256
    }
}
if (-not $paperCandidateValid) {
    Save-PinnedArtifact -Uri $paper.url -Destination $paperServerCandidate -Algorithm SHA256 `
        -ExpectedHash $paper.sha256 -ExpectedSize ([long]$paper.size) -MaximumBytes ([long]$paper.size) -StopAt $root
}

$legacyPack = Join-Path $root 'resourcepacks/build/CopiMineResourcePack.zip'
Assert-AtomicFileDestination -Path $legacyPack -Description 'legacy resource-pack source' -StopAt $root
if (-not (Test-Path -LiteralPath $legacyPack -PathType Leaf)) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'resourcepacks/build-resourcepack.ps1') -SkipServerProperties
    if ($LASTEXITCODE -ne 0) { throw 'Legacy resource-pack fixture build failed' }
}
$legacyHash = (Get-FileHash -LiteralPath $legacyPack -Algorithm SHA256).Hash.ToLowerInvariant()
if ($legacyHash -ne $profile.resourcePackMigration.legacyPackSha256) {
    throw 'Legacy resource pack does not match the pinned migration source'
}

$toolchain = Join-Path $root 'build/minecraft-26.3/toolchain'
$minecraftClient = Join-Path $toolchain 'minecraft-26.3-client.jar'
Assert-AtomicFileDestination -Path $minecraftClient -Description 'Minecraft client cache' -StopAt $root
$minecraftClientValid = $false
if (Test-Path -LiteralPath $minecraftClient -PathType Leaf) {
    if ((Get-Item -LiteralPath $minecraftClient).Length -eq [long]$profile.minecraftClient.size) {
        $minecraftClientValid = (Get-FileHash -LiteralPath $minecraftClient -Algorithm SHA1).Hash.ToLowerInvariant() -eq $profile.minecraftClient.sha1
    }
}
if (-not $minecraftClientValid) {
    Save-PinnedArtifact -Uri $profile.minecraftClient.url -Destination $minecraftClient -Algorithm SHA1 `
        -ExpectedHash $profile.minecraftClient.sha1 -ExpectedSize ([long]$profile.minecraftClient.size) `
        -MaximumBytes ([long]$profile.minecraftClient.size) -StopAt $root
}

$clientModCandidateDirectory = Join-Path $root 'build/minecraft-26.3/client-mods'
Assert-ProfileDirectoryDestination -Path $clientModCandidateDirectory -Description 'client mod candidate directory' -StopAt $root
New-Item -ItemType Directory -Path $clientModCandidateDirectory -Force | Out-Null
Assert-DirectoryTreeNoReparse -Path $clientModCandidateDirectory -Description 'client mod candidate directory' -StopAt $root
if ($clientModsLock.minecraftVersion -cne $profile.minecraftVersion -or @($clientModsLock.modules).Count -eq 0) {
    throw 'Client mod lock must contain candidates for the pinned Minecraft version'
}
$fabricApiModules = @($clientModsLock.modules | Where-Object { $_.project -ceq 'fabric-api' })
if ($clientModsLock.loader -cne 'fabric' -or -not $profile.fabric -or
    $profile.fabric.loader -notmatch '^\d+\.\d+\.\d+$' -or $fabricApiModules.Count -ne 1) {
    throw 'Client mod lock must match the Fabric loader profile and contain exactly one Fabric API module'
}
$fabricApiModule = $fabricApiModules[0]
if ($fabricApiModule.version -cne $profile.fabric.api -or
    $fabricApiModule.filename -cne ('fabric-api-' + $profile.fabric.api + '.jar')) {
    throw 'Client mod lock Fabric API version must match the API used to build CopiMineClient'
}
foreach ($clientMod in $clientModsLock.modules) {
    if (-not $clientMod.filename -or $clientMod.filename -match '[\\/:]' -or
        -not $clientMod.modId -or $clientMod.modId -notmatch '^[a-z0-9_.-]{1,64}$' -or
        [string]::IsNullOrWhiteSpace([string]$clientMod.fabricMetadataVersion) -or
        $clientMod.sha512 -notmatch '^[0-9a-f]{128}$' -or [long]$clientMod.size -le 0 -or
        [long]$clientMod.size -gt 100000000) {
        throw 'Client mod lock contains an invalid filename, SHA-512, or size'
    }
    $modUri = $null
    if (-not [Uri]::TryCreate([string]$clientMod.url, [UriKind]::Absolute, [ref]$modUri) -or
        $modUri.Scheme -ne 'https' -or $modUri.Host -notin @('cdn.modrinth.com', 'edge.forgecdn.net')) {
        throw ('Client mod URL is not hosted on an approved lock CDN: ' + $clientMod.filename)
    }
    if ($modUri.Host -eq 'cdn.modrinth.com' -and
        $modUri.AbsolutePath -cnotmatch ('^/data/' + [regex]::Escape([string]$clientMod.projectId) + '/versions/' +
            [regex]::Escape([string]$clientMod.versionId) + '/')) {
        throw ('Client mod URL does not match its locked Modrinth project and version: ' + $clientMod.filename)
    }
    $trustedForgeCdnSources = @(
        'https://edge.forgecdn.net/files/9023/814/CustomSkinLoader_Universal-15.1.jar',
        'https://edge.forgecdn.net/files/9021/707/mc-armor-hud-8.1.1.6-26.3-fabric.jar'
    )
    if ($modUri.Host -eq 'edge.forgecdn.net' -and $clientMod.url -cnotin $trustedForgeCdnSources) {
        throw ('Client mod URL is not an explicitly pinned CurseForge artifact: ' + $clientMod.filename)
    }
    $modCandidate = Join-Path $clientModCandidateDirectory $clientMod.filename
    Assert-AtomicFileDestination -Path $modCandidate -Description ('client mod candidate ' + $clientMod.filename) -StopAt $root
    $valid = $false
    if ((Test-Path -LiteralPath $modCandidate -PathType Leaf) -and
        (Get-Item -LiteralPath $modCandidate).Length -eq [long]$clientMod.size) {
        $valid = (Get-FileHash -LiteralPath $modCandidate -Algorithm SHA512).Hash.ToLowerInvariant() -eq $clientMod.sha512
    }
    if (-not $valid) {
        Save-PinnedArtifact -Uri $clientMod.url -Destination $modCandidate -Algorithm SHA512 `
            -ExpectedHash $clientMod.sha512 -ExpectedSize ([long]$clientMod.size) `
            -MaximumBytes ([long]$clientMod.size) -StopAt $root
    }
    $modArchive = [IO.Compression.ZipFile]::OpenRead($modCandidate)
    try {
        $metadataEntry = $modArchive.GetEntry('fabric.mod.json')
        if (-not $metadataEntry -or $metadataEntry.Length -gt 256KB) {
            throw ('Pinned client mod has missing or oversized fabric.mod.json metadata: ' + $clientMod.filename)
        }
        $metadataStream = $metadataEntry.Open()
        $metadataReader = $null
        try {
            $metadataReader = [IO.StreamReader]::new($metadataStream, [Text.UTF8Encoding]::new($false, $true), $true)
            $modMetadata = ConvertFrom-Json -InputObject $metadataReader.ReadToEnd() -ErrorAction Stop
        } finally {
            if ($metadataReader) { $metadataReader.Dispose() } else { $metadataStream.Dispose() }
        }
        if ($modMetadata.id -cne $clientMod.modId -or $modMetadata.version -cne $clientMod.fabricMetadataVersion) {
            throw ('Pinned client mod metadata differs from its locked id or version: ' + $clientMod.filename)
        }
    } finally { $modArchive.Dispose() }
}

& (Join-Path $PSScriptRoot 'BuildMigrationPlugins.ps1') -JavaHome $BuildJavaHome
if ($LASTEXITCODE -ne 0) { throw 'First-party Paper 26.3 plugin build failed' }

$gradle = Join-Path $toolchain (('gradle-' + $profile.gradle.version) + '/bin/gradle.bat')
$clientBuildOutput = Join-Path $root 'build/minecraft-26.3/client'
Assert-DirectoryTreeNoReparse -Path $clientBuildOutput -Description 'CopiMineClient build output' -StopAt $root
$clientProject = Join-Path $root 'tools/minecraft-26.3/client'
$clientGradleProjectCache = Join-Path $root 'build/minecraft-26.3/gradle-project-cache/client'
$clientLocalProjectCache = Join-Path $clientProject '.gradle'
Reset-GeneratedDirectory -Path $clientBuildOutput -Description 'CopiMineClient build output' -StopAt $root
Reset-GeneratedDirectory -Path $clientGradleProjectCache -Description 'Gradle client project cache' -StopAt $root
Reset-GeneratedDirectory -Path $clientLocalProjectCache -Description 'Gradle client local cache' -StopAt $root
$previousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $BuildJavaHome).Path
    & $gradle --no-daemon --project-cache-dir $clientGradleProjectCache -p $clientProject test jar
    if ($LASTEXITCODE -ne 0) { throw 'CopiMineClient 26.3 build and tests failed' }
    Assert-DirectoryTreeNoReparse -Path $clientBuildOutput -Description 'CopiMineClient build output' -StopAt $root
    Assert-DirectoryTreeNoReparse -Path $clientGradleProjectCache -Description 'Gradle client project cache' -StopAt $root
    Assert-DirectoryTreeNoReparse -Path $clientLocalProjectCache -Description 'Gradle client local cache' -StopAt $root
    $clientArtifactPin = $profile.clientArtifact
    $clientJar = Join-Path $clientBuildOutput ('libs/' + $clientArtifactPin.filename)
    Assert-AtomicFileDestination -Path $clientJar -Description 'CopiMineClient build artifact' -StopAt $root
    if (-not $clientArtifactPin -or $clientArtifactPin.sha512 -notmatch '^[0-9a-f]{128}$' -or
        [long]$clientArtifactPin.size -le 0 -or -not (Test-Path -LiteralPath $clientJar -PathType Leaf) -or
        (Get-Item -LiteralPath $clientJar).Length -ne [long]$clientArtifactPin.size -or
        (Get-FileHash -LiteralPath $clientJar -Algorithm SHA512).Hash.ToLowerInvariant() -ne $clientArtifactPin.sha512) {
        throw 'CopiMineClient build output differs from its pinned artifact digest and size'
    }
} finally {
    $env:JAVA_HOME = $previousJava
}

& (Join-Path $PSScriptRoot 'BuildCoreProtectMigration.ps1') -JavaHome $BuildJavaHome
if ($LASTEXITCODE -ne 0) { throw 'Pinned CoreProtect 26.3 source build failed' }

$grim = $pluginLock.modules | Where-Object { $_.pluginName -eq 'GrimAC' } | Select-Object -First 1
if (-not $grim) { throw 'Pinned GrimAC migration candidate is missing from the server lock' }
$grimSource = Join-Path (Join-Path $root 'build/minecraft-26.3/server-plugins') $grim.upstreamFilename
Assert-AtomicFileDestination -Path $grimSource -Description 'Grim source cache' -StopAt $root
$grimCacheValid = $false
if (Test-Path -LiteralPath $grimSource -PathType Leaf) {
    if ((Get-Item -LiteralPath $grimSource).Length -eq [long]$grim.upstreamSize) {
        $grimCacheValid = (Get-FileHash -LiteralPath $grimSource -Algorithm SHA512).Hash.ToLowerInvariant() -eq $grim.upstreamSha512
    }
}
if (-not $grimCacheValid) {
    Save-PinnedArtifact -Uri $grim.url -Destination $grimSource -Algorithm SHA512 `
        -ExpectedHash $grim.upstreamSha512 -ExpectedSize ([long]$grim.upstreamSize) `
        -MaximumBytes ([long]$grim.upstreamSize) -StopAt $root
}
& (Join-Path $root $grim.patch) -JavaHome $GrimPatchJavaHome
if ($LASTEXITCODE -ne 0) { throw 'Pinned GrimAC compatibility patch build failed' }

& $PythonExe (Join-Path $PSScriptRoot 'install_migration_server_plugins.py') --stage-only
if ($LASTEXITCODE -ne 0) { throw 'Pinned Paper 26.3 server plugin staging failed' }

& $PythonExe (Join-Path $root 'resourcepacks/build-migration-pack.py')
if ($LASTEXITCODE -ne 0) { throw 'Minecraft 26.3 resource-pack migration build failed' }

Write-Output 'Pinned Minecraft 26.3 client, Paper server JAR, pack, first-party plugins and server plugin candidates are staged; native runtime remains unverified.'
