#requires -Version 5.1
<#
.SYNOPSIS
    Populates an isolated Maven repository with the pinned Paper 26.3 compile
    classpath and exports the environment variables the first-party plugin build
    scripts read.

.DESCRIPTION
    The End Event plugin is compiled against Paper 26.3, so anything that builds
    it needs the pinned compile classpath on disk. The plugin release gate used
    to be the only place that produced it, which meant any other job that built
    a plugin first had to reimplement the download - and the staging job in CI
    simply failed with "Locked compile dependency is missing".

    This script is the single source of truth for that preparation. It verifies
    every artifact against the checked-in locks before writing it, keeps
    everything inside the caller's temporary root, and refuses paths that try to
    escape it.

.PARAMETER RepositoryRoot
    Workspace root that holds tools/minecraft-26.3.

.PARAMETER MavenRepository
    Destination directory for the isolated repository.

.PARAMETER TempRoot
    Temporary root the destination must stay inside.

.OUTPUTS
    PSCustomObject with MavenRepository, CompileClasspath, PaperApiJar,
    VoiceChatApiJar.
#>
param(
    [Parameter(Mandatory = $true)]
    [string] $RepositoryRoot,
    [Parameter(Mandatory = $true)]
    [string] $MavenRepository,
    [Parameter(Mandatory = $true)]
    [string] $TempRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DownloadPinnedArtifact.ps1')

$repo = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$tempRoot = [IO.Path]::GetFullPath($TempRoot)
$tempRootPrefix = $tempRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$mavenRepository = [IO.Path]::GetFullPath($MavenRepository)
if (-not $mavenRepository.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "The isolated Maven repository escapes the temporary root: $mavenRepository"
}
$mavenRepositoryFullPath = $mavenRepository.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar

$profileLockPath = Join-Path $repo 'tools/minecraft-26.3/profile.lock.json'
$compileLockPath = Join-Path $repo 'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json'
$profileLock = Get-Content -LiteralPath $profileLockPath -Raw | ConvertFrom-Json
$compileLock = Get-Content -LiteralPath $compileLockPath -Raw | ConvertFrom-Json
if ($compileLock.schemaVersion -ne 1 -or
    $compileLock.artifactRepository -ne 'https://repo.papermc.io/repository/maven-public' -or
    -not $compileLock.dependencies) {
    throw "Invalid Java compile dependency lock: $compileLockPath"
}
$migrationDependencies = @($compileLock.paper26_3Dependencies)
$lockedDependencies = @($compileLock.dependencies)
if ($migrationDependencies.Count -eq 0) {
    throw 'The 26.3 compile dependency lock must include the Paper migration API.'
}
if ($compileLock.paper26_3Api -cne [string]$profileLock.paper.api) {
    throw 'The 26.3 compile dependency lock must match the profile-locked Paper API.'
}

function Save-LockedDependency {
    param(
        [string] $RelativePath,
        [string] $Repository,
        [string] $DestinationRoot,
        [string] $DestinationPrefix
    )
    $expectedPath = [IO.Path]::GetFullPath((Join-Path $DestinationRoot $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)))
    if (-not $expectedPath.StartsWith($DestinationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Pinned dependency path escapes its isolated repository: $RelativePath"
    }
    return $expectedPath
}

foreach ($locked in $migrationDependencies) {
    if ($locked.path -notmatch '^[A-Za-z0-9._+/-]+\.jar$' -or $locked.path.Split('/') -contains '..' -or
        $locked.size -le 0 -or $locked.sha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Invalid Paper 26.3 compile dependency lock entry: $($locked.path)"
    }
    $expectedPath = Save-LockedDependency -RelativePath $locked.path -Repository $mavenRepository -DestinationRoot $mavenRepository -DestinationPrefix $mavenRepositoryFullPath
    $encodedPath = ($locked.path.Split('/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
    $artifactUri = $compileLock.artifactRepository.TrimEnd('/') + '/' + $encodedPath
    Save-PinnedArtifact -Uri $artifactUri -Destination $expectedPath -Algorithm SHA256 `
        -ExpectedHash $locked.sha256 -ExpectedSize $locked.size -MaximumBytes $locked.size -StopAt $tempRoot
}

$paper263ApiRelativePath = 'io/papermc/paper/paper-api/' + [string]$profileLock.paper.api + '/paper-api-' + [string]$profileLock.paper.api + '.jar'
$paper263ApiJar = [IO.Path]::GetFullPath((Join-Path $mavenRepository $paper263ApiRelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)))
if (-not $paper263ApiJar.StartsWith($mavenRepositoryFullPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Paper 26.3 API path escapes its isolated Maven repository.'
}
$repositoryPaper263ApiUri = $compileLock.artifactRepository.TrimEnd('/') + '/' + (($paper263ApiRelativePath.Split('/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/')
if ([string]$profileLock.paper.apiUrl -cne $repositoryPaper263ApiUri) {
    throw 'The profile-locked Paper 26.3 API URL must match the exact repository path.'
}
$paper263ApiSize = [long]$profileLock.paper.apiSize
if ($paper263ApiSize -le 0 -or $paper263ApiSize -gt 100000000) {
    throw 'Paper 26.3 API size must match the exact profile lock.'
}
Save-PinnedArtifact -Uri ([string]$profileLock.paper.apiUrl) -Destination $paper263ApiJar -Algorithm SHA256 `
    -ExpectedHash ([string]$profileLock.paper.apiSha256) -ExpectedSize $paper263ApiSize `
    -MaximumBytes $paper263ApiSize -StopAt $tempRoot

$verifiedClasspath = [System.Collections.Generic.List[string]]::new()
foreach ($locked in $lockedDependencies) {
    if ($locked.path -notmatch '^[A-Za-z0-9._+/-]+\.jar$' -or $locked.path.Split('/') -contains '..' -or
        $locked.size -le 0 -or $locked.sha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Invalid Java compile dependency lock entry: $($locked.path)"
    }
    $expectedPath = Save-LockedDependency -RelativePath $locked.path -Repository $mavenRepository -DestinationRoot $mavenRepository -DestinationPrefix $mavenRepositoryFullPath
    $encodedPath = ($locked.path.Split('/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
    $artifactUri = $compileLock.artifactRepository.TrimEnd('/') + '/' + $encodedPath
    Save-PinnedArtifact -Uri $artifactUri -Destination $expectedPath -Algorithm SHA256 `
        -ExpectedHash $locked.sha256 -ExpectedSize $locked.size -MaximumBytes $locked.size -StopAt $tempRoot
    $dependencyItem = Get-Item -LiteralPath $expectedPath -ErrorAction Stop
    if ($dependencyItem.Length -ne [long]$locked.size) {
        throw "Java compile dependency size mismatch at $($locked.path): expected=$($locked.size) actual=$($dependencyItem.Length)"
    }
    $dependencySha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $expectedPath).Hash.ToLowerInvariant()
    if ($dependencySha256 -ne $locked.sha256) {
        throw "Java compile dependency SHA256 mismatch at $($locked.path): $dependencySha256"
    }
    $verifiedClasspath.Add($expectedPath)
}

$compileClasspath = [string]::Join([IO.Path]::PathSeparator, $verifiedClasspath.ToArray())
if ($compileClasspath.Length -gt 16000) {
    throw "Pinned Paper compile classpath exceeds the safe Windows command-line budget: $($compileClasspath.Length) characters."
}

# The server-plugin lock pins the Voice Chat API the plugin compiles against.
$serverPluginLock = Get-Content -LiteralPath (Join-Path $repo 'tools/minecraft-26.3/server-plugins.lock.json') -Raw | ConvertFrom-Json
$lockedVoicechat = @(@($serverPluginLock.modules) + @($serverPluginLock.unchangedBaseline) |
    Where-Object { $_.pluginName -eq 'voicechat' })
if ($lockedVoicechat.Count -ne 1 -or -not $lockedVoicechat[0].url -or
    -not $lockedVoicechat[0].size -or -not $lockedVoicechat[0].sha256) {
    throw 'The migration server-plugin lock must contain one fully pinned Voice Chat API artifact.'
}
$voicechatFilename = [string]$lockedVoicechat[0].filename
if ($voicechatFilename -notmatch '^[A-Za-z0-9][A-Za-z0-9._+-]*\.jar$' -or
    [IO.Path]::GetFileName($voicechatFilename) -cne $voicechatFilename) {
    throw 'The migration Voice Chat artifact filename must be a simple JAR filename.'
}
$voicechatCandidate = [IO.Path]::GetFullPath((Join-Path $tempRoot $voicechatFilename))
if (-not $voicechatCandidate.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The migration Voice Chat artifact destination escapes the temporary directory.'
}
Save-PinnedArtifact -Uri $lockedVoicechat[0].url -Destination $voicechatCandidate -Algorithm SHA256 `
    -ExpectedHash $lockedVoicechat[0].sha256 -ExpectedSize $lockedVoicechat[0].size `
    -MaximumBytes $lockedVoicechat[0].size -StopAt $tempRoot

# Exported so a caller can simply dot-source or invoke this script and then run
# any first-party plugin build script without knowing these names itself.
$env:COPIMINE_MAVEN_REPOSITORY = $mavenRepository
$env:PAPER_COMPILE_DEPS = $compileClasspath
$env:PAPER_API_JAR = $paper263ApiJar
$env:PAPER_VOICECHAT_API_JAR = $voicechatCandidate

[PSCustomObject]@{
    MavenRepository = $mavenRepository
    CompileClasspath = $compileClasspath
    PaperApiJar = $paper263ApiJar
    VoiceChatApiJar = $voicechatCandidate
}