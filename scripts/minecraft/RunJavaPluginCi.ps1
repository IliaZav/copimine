param(
    [Parameter(Mandatory = $true)]
    [string] $JavaHome,
    [string] $WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $TemporaryDirectory = [IO.Path]::GetTempPath(),
    [string] $PythonExe = 'python',
    [switch] $KeepBuildOutputs
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
. (Join-Path $PSScriptRoot 'DownloadPinnedArtifact.ps1')
. (Join-Path $PSScriptRoot 'MigrationOutputSnapshot.ps1')

function Stop-MigrationGradleDaemon {
    param(
        [Parameter(Mandatory = $true)][string]$GradleHome,
        [Parameter(Mandatory = $true)][string]$UserHome,
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot
    )

    $gradleLauncher = Join-Path $GradleHome 'bin\gradle.bat'
    $workspacePath = [IO.Path]::GetFullPath($WorkspaceRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $findWorkspaceDaemons = {
        @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop | Where-Object {
            $_.CommandLine -and
            $_.CommandLine.Contains('org.gradle.launcher.daemon.bootstrap.GradleDaemon') -and
            $_.CommandLine.IndexOf($workspacePath, [StringComparison]::OrdinalIgnoreCase) -ge 0
        })
    }

    if (-not (Test-Path -LiteralPath $gradleLauncher -PathType Leaf)) {
        $remaining = & $findWorkspaceDaemons
        if ($remaining.Count -gt 0) {
            throw "Cannot restore migration outputs while a Gradle daemon is active and its launcher is missing: $gradleLauncher"
        }
        return
    }

    $priorGradleUserHome = $env:GRADLE_USER_HOME
    try {
        $env:GRADLE_USER_HOME = $UserHome
        Push-Location -LiteralPath $WorkspaceRoot
        try {
            & $gradleLauncher --stop
            if ($LASTEXITCODE -ne 0) {
                throw "Could not stop the isolated Gradle daemon; exit code $LASTEXITCODE."
            }
        } finally {
            Pop-Location
        }
    } finally {
        if ($null -eq $priorGradleUserHome) {
            Remove-Item Env:\GRADLE_USER_HOME -ErrorAction SilentlyContinue
        } else {
            $env:GRADLE_USER_HOME = $priorGradleUserHome
        }
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $remaining = & $findWorkspaceDaemons
        if ($remaining.Count -eq 0) { return }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    $processIds = (@($remaining | ForEach-Object { $_.ProcessId }) -join ', ')
    throw "Gradle daemons from this workspace remain active after --stop (PID: $processIds); output restoration was withheld."
}

function Assert-MigrationTemporaryRoot {
    param(
        [Parameter(Mandatory = $true)][string]$TemporaryBase,
        [Parameter(Mandatory = $true)][string]$TemporaryRoot
    )

    $basePath = [IO.Path]::GetFullPath($TemporaryBase).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $basePrefix = $basePath + [IO.Path]::DirectorySeparatorChar
    $rootPath = [IO.Path]::GetFullPath($TemporaryRoot)
    if (-not $rootPath.StartsWith($basePrefix, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $rootPath) -notmatch '^copimine-java-plugin-ci-\d+-[0-9a-f]{32}$') {
        throw "Migration temporary root is not a generated child of the selected temporary directory: $rootPath"
    }
    Assert-MigrationSnapshotDirectoryNoReparse -Path $rootPath
    return $rootPath
}

function Write-MigrationRecoveryRecord {
    param(
        [Parameter(Mandatory = $true)][string]$RecoveryRecordPath,
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
        [Parameter(Mandatory = $true)][string]$TemporaryRoot,
        [Parameter(Mandatory = $true)][string]$TemporaryBase,
        [Parameter(Mandatory = $true)][string]$ManifestPath,
        [Parameter(Mandatory = $true)][string]$WorkspaceRevision
    )

    if (Test-Path -LiteralPath $RecoveryRecordPath) {
        throw "A prior migration recovery record must be resolved before writing another: $RecoveryRecordPath"
    }
    $temporaryRootFull = [IO.Path]::GetFullPath($TemporaryRoot)
    $expectedManifest = Join-Path (Join-Path $temporaryRootFull 'workspace-output-snapshot') 'manifest.json'
    if (-not [string]::Equals([IO.Path]::GetFullPath($ManifestPath), $expectedManifest, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration recovery record manifest is outside its generated snapshot directory.'
    }
    $record = [pscustomobject]@{
        SchemaVersion = 2
        WorkspaceRoot = [IO.Path]::GetFullPath($WorkspaceRoot)
        WorkspaceRevision = $WorkspaceRevision
        TemporaryBase = [IO.Path]::GetFullPath($TemporaryBase)
        TemporaryRoot = $temporaryRootFull
        ManifestPath = $expectedManifest
        ManifestSha256 = (Get-FileHash -LiteralPath $expectedManifest -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    }
    $temporaryRecordPath = $RecoveryRecordPath + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    $recordJson = ConvertTo-Json -InputObject $record -Depth 4
    [IO.File]::WriteAllText($temporaryRecordPath, $recordJson, [Text.UTF8Encoding]::new($false))
    [IO.File]::Move($temporaryRecordPath, $RecoveryRecordPath)
}

function Read-MigrationRecoveryRecord {
    param(
        [Parameter(Mandatory = $true)][string]$RecoveryRecordPath,
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
        [Parameter(Mandatory = $true)][string]$WorkspaceRevision,
        [Parameter(Mandatory = $true)][string[]]$ExpectedRelativePaths
    )

    if (-not (Test-Path -LiteralPath $RecoveryRecordPath -PathType Leaf)) { return $null }
    $recordFile = Get-Item -LiteralPath $RecoveryRecordPath -Force -ErrorAction Stop
    if ($recordFile.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Migration recovery record cannot be a reparse point: $RecoveryRecordPath"
    }
    $record = Get-Content -LiteralPath $RecoveryRecordPath -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    if ($record.schemaVersion -notin @(1, 2) -or -not $record.workspaceRoot -or -not $record.workspaceRevision -or
        -not $record.temporaryBase -or -not $record.temporaryRoot -or -not $record.manifestPath) {
        throw "Invalid migration recovery record: $RecoveryRecordPath"
    }
    $recordWorkspace = [IO.Path]::GetFullPath([string]$record.workspaceRoot)
    $expectedWorkspace = [IO.Path]::GetFullPath($WorkspaceRoot)
    if (-not [string]::Equals($recordWorkspace.TrimEnd([IO.Path]::DirectorySeparatorChar),
            $expectedWorkspace.TrimEnd([IO.Path]::DirectorySeparatorChar), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration recovery record belongs to a different workspace.'
    }
    $recordRevision = [string]$record.workspaceRevision
    if ($recordRevision -notmatch '^[0-9a-f]{40}$') { throw 'Migration recovery record commit SHA is invalid.' }
    if (-not [string]::Equals($recordRevision, $WorkspaceRevision, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Migration recovery record belongs to commit $recordRevision, but this checkout is at $WorkspaceRevision; automatic restoration was withheld."
    }
    $previousTempBase = [IO.Path]::GetFullPath([string]$record.temporaryBase)
    Assert-MigrationSnapshotDirectoryNoReparse -Path $previousTempBase
    $previousTempRoot = Assert-MigrationTemporaryRoot -TemporaryBase $previousTempBase -TemporaryRoot ([string]$record.temporaryRoot)
    $expectedManifest = Join-Path (Join-Path $previousTempRoot 'workspace-output-snapshot') 'manifest.json'
    if (-not [string]::Equals([IO.Path]::GetFullPath([string]$record.manifestPath), $expectedManifest, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Migration recovery record manifest is outside its generated snapshot directory.'
    }
    $previousManifest = $expectedManifest
    if (-not $record.manifestSha256) {
        throw 'Migration recovery record has no manifest integrity hash; automatic restoration was withheld.'
    }
    $expectedManifestHash = [string]$record.manifestSha256
    if ($expectedManifestHash -notmatch '^[0-9a-f]{64}$') {
        throw 'Migration recovery record manifest SHA-256 is invalid.'
    }
    $snapshot = Read-MigrationOutputSnapshot -ManifestPath $previousManifest -WorkspaceRoot $expectedWorkspace `
        -ExpectedRelativePaths $ExpectedRelativePaths -ExpectedManifestSha256 $expectedManifestHash
    if ($snapshot.SchemaVersion -lt 3) {
        throw 'Migration recovery snapshot does not include payload integrity hashes; automatic restoration was withheld.'
    }
    return [pscustomobject]@{
        TemporaryBase = $previousTempBase
        TemporaryRoot = $previousTempRoot
        UserHome = Join-Path $previousTempRoot 'gradle-user-home'
        ManifestPath = $expectedManifest
        Snapshot = $snapshot
    }
}

function Remove-MigrationTemporaryRoot {
    param(
        [Parameter(Mandatory = $true)][string]$TemporaryBase,
        [Parameter(Mandatory = $true)][string]$TemporaryRoot
    )

    $rootPath = Assert-MigrationTemporaryRoot -TemporaryBase $TemporaryBase -TemporaryRoot $TemporaryRoot
    Remove-Item -LiteralPath $rootPath -Recurse -Force -ErrorAction Stop
}

function Get-MigrationWorkspaceRevision {
    param([Parameter(Mandatory = $true)][string]$WorkspaceRoot)

    $gitApplications = @($ExecutionContext.InvokeCommand.GetCommands('git.exe', [Management.Automation.CommandTypes]::Application, $true))
    if ($gitApplications.Count -eq 0 -or -not [IO.Path]::IsPathRooted($gitApplications[0].Path) -or -not [IO.File]::Exists($gitApplications[0].Path)) {
        throw 'A Git executable application could not be resolved safely for migration recovery.'
    }

    $gitExecutable = $gitApplications[0].Path
    $revision = (& $gitExecutable -C $WorkspaceRoot rev-parse --verify HEAD 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $revision -notmatch '^[0-9a-f]{40}$') {
        throw "Could not resolve the current Git HEAD for migration recovery in $WorkspaceRoot."
    }
    return $revision.ToLowerInvariant()
}

function Preserve-MigrationOutputStateBeforeRecovery {
    param(
        [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
        [Parameter(Mandatory = $true)][string]$WorkspaceHash,
        [Parameter(Mandatory = $true)][string]$RecoveryStateDirectory,
        [Parameter(Mandatory = $true)][string]$WorkspaceRevision,
        [Parameter(Mandatory = $true)]$PreviousSnapshot
    )

    $archiveName = 'r-{0}-{1}-{2}' -f $WorkspaceHash.Substring(0, 12), [DateTime]::UtcNow.ToString('yyMMddHHmmss'), [guid]::NewGuid().ToString('N').Substring(0, 8)
    $archiveDirectory = Join-Path $RecoveryStateDirectory $archiveName
    $relativePaths = @($PreviousSnapshot.Items | ForEach-Object { $_.RelativePath })
    $preservedSnapshot = New-MigrationOutputSnapshot -WorkspaceRoot $WorkspaceRoot -SnapshotDirectory $archiveDirectory -RelativePaths $relativePaths
    $context = [pscustomobject]@{
        SchemaVersion = 1
        Purpose = 'Preserved workspace output state before interrupted gate recovery.'
        WorkspaceRoot = [IO.Path]::GetFullPath($WorkspaceRoot)
        WorkspaceRevision = $WorkspaceRevision
        CreatedAtUtc = [DateTime]::UtcNow
        PreviousRecoveryManifestPath = $PreviousSnapshot.ManifestPath
        PreservedSnapshotManifestPath = $preservedSnapshot.ManifestPath
    }
    $contextJson = ConvertTo-Json -InputObject $context -Depth 4
    [IO.File]::WriteAllText((Join-Path $archiveDirectory 'recovery-context.json'), $contextJson, [Text.UTF8Encoding]::new($false))
    return $preservedSnapshot
}

$repo = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
$jdk = (Resolve-Path -LiteralPath $JavaHome).Path
$java = Join-Path $jdk 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw "Pinned Java 21 executable was not found: $java"
}
$javaVersion = (& $java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^openjdk 21\.0\.12\.1(?:\s|$)') {
    throw "The Java plugin release gate requires the pinned Temurin 21.0.12.1 release; found: $javaVersion"
}
$pythonCommand = Get-Command -Name $PythonExe -ErrorAction Stop
if (-not $pythonCommand.Source) {
    throw "Python executable did not resolve to a filesystem path: $PythonExe"
}
$pythonVersion = (& $pythonCommand.Source --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $pythonVersion -notmatch '(?m)^Python 3\.13\.16(?:\s|$)') {
    throw "The Java plugin release gate requires Python 3.13.16; found: $pythonVersion"
}
$pythonDirectory = Split-Path -Parent $pythonCommand.Source
$pythonScripts = Join-Path $pythonDirectory 'Scripts'

$tempBase = (Resolve-Path -LiteralPath $TemporaryDirectory).Path
$isolatedGradleHome = Join-Path $repo 'CopiMineClient/.gradle-dist/gradle-8.10.2'
$workspaceHashAlgorithm = [Security.Cryptography.SHA256]::Create()
try {
    $workspaceHashBytes = $workspaceHashAlgorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($repo.ToUpperInvariant()))
} finally {
    $workspaceHashAlgorithm.Dispose()
}
$workspaceHash = [BitConverter]::ToString($workspaceHashBytes).Replace('-', '').ToLowerInvariant()
$recoveryBase = if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    [IO.Path]::GetTempPath()
} else {
    $env:LOCALAPPDATA
}
$recoveryStateDirectory = Join-Path $recoveryBase 'CopiMine\minecraft-26.3-migration\gate-state'
New-Item -ItemType Directory -Path $recoveryStateDirectory -Force -ErrorAction Stop | Out-Null
$recoveryStateDirectory = (Resolve-Path -LiteralPath $recoveryStateDirectory -ErrorAction Stop).Path
Assert-MigrationSnapshotDirectoryNoReparse -Path $recoveryStateDirectory
$repoPrefix = [IO.Path]::GetFullPath($repo).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if ([IO.Path]::GetFullPath($recoveryStateDirectory).StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Migration gate recovery state must be outside the workspace.'
}
$recoveryRecordPath = Join-Path $recoveryStateDirectory "recovery-$workspaceHash.json"
$gateLockPath = Join-Path $recoveryStateDirectory "gate-$workspaceHash.lock"
$gateLockStream = $null
$gateEnvironmentNames = @(
    'JAVA_HOME', 'PATH', 'GRADLE_USER_HOME', 'PAPER_PLACEHOLDER_API_JAR', 'PAPER_VOICECHAT_API_JAR',
    'COPIMINE_MAVEN_REPOSITORY', 'PAPER_COMPILE_DEPS', 'PAPER_API_JAR',
    'PYTHONDONTWRITEBYTECODE', 'PYTEST_ADDOPTS'
)
$gateEnvironmentSnapshot = @{}
foreach ($name in $gateEnvironmentNames) {
    $value = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
    $wasSet = $null -ne $value
    $gateEnvironmentSnapshot[$name] = [pscustomobject]@{ WasSet = $wasSet; Value = $value }
}

try {
try {
    $gateLockStream = [IO.File]::Open($gateLockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
} catch [IO.IOException] {
    throw "Another Java plugin CI gate is already active for this workspace (lock: $gateLockPath)."
}
$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + [IO.Path]::PathSeparator + $pythonDirectory + [IO.Path]::PathSeparator + $pythonScripts + [IO.Path]::PathSeparator + $env:PATH
$workspaceRevision = Get-MigrationWorkspaceRevision -WorkspaceRoot $repo

$snapshotPaths = @(
    'copimine-world-core/build', 'copimine-artifacts/build', 'copimine-end-event/build',
    'copimine-economy-core/build', 'copimine-election-core/build', 'copimine-narcotics/build',
    'copimine-admin-plugin/build', 'minecraft/server/plugins/AuthEffects/build',
    'CopiMineClient/build', 'CopiMineClient/.gradle', 'CopiMineClient/.gradle-dist', 'resourcepacks/build',
    'tests/build',
    'CopiMineClient/src/main/resources/assets/copimineclient/geometry/end_rift_tentacle.json',
    'CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_tentacle_hd.png',
    'resourcepacks/src/assets/copimine/textures/item/end_event_rift_tentacle_hd.png',
    'resourcepacks/src/assets/copimine/models/item/end_event_rift_tentacle.json',
    'minecraft/server/server.properties',
    'thirdparty/client-mods/CopiMineClient-0.1.1.jar', 'thirdparty/thirdparty_manifest.json',
    'thirdparty/checksums.txt', 'thirdparty/CopiMineMods.zip', 'thirdparty/CopiMineMods.sha1',
    'thirdparty/CopiMineMods.sha256', 'thirdparty/_modpack_stage',
    'admin-web/frontend/assets/public-data/modpack_snapshot.json',
    'copimine-world-core/CopiMineWorldCore.jar', 'copimine-artifacts/CopiMineArtifacts.jar',
    'copimine-end-event/CopiMineEndEvent.jar', 'copimine-economy-core/CopiMineEconomyCore.jar',
    'copimine-election-core/CopiMineElectionCore.jar', 'copimine-narcotics/CopiMineNarcotics.jar',
    'copimine-admin-plugin/CopiMineUltimateAdminPlus.jar',
    'minecraft/server/plugins/CopiMineWorldCore.jar', 'minecraft/server/plugins/CopiMineArtifacts.jar',
    'minecraft/server/plugins/CopiMineEndEvent.jar', 'minecraft/server/plugins/CopiMineEconomyCore.jar',
    'minecraft/server/plugins/CopiMineElectionCore.jar', 'minecraft/server/plugins/CopiMineNarcotics.jar',
    'minecraft/server/plugins/CopiMineUltimateAdminPlus.jar', 'minecraft/server/plugins/AuthEffects.jar',
    'minecraft/server/plugins/GrimAC.jar', 'minecraft/server/plugins/Chunky-Bukkit-1.4.40.jar',
    'minecraft/server/plugins/SeeMore-1.0.2.jar',
    'minecraft/server/plugins/Chunky', 'minecraft/server/plugins/SeeMore',
    'minecraft/server/plugins/CopiMineWorldCore', 'minecraft/server/plugins/CopiMineArtifacts',
    'minecraft/server/plugins/CopiMineEndEvent', 'minecraft/server/plugins/CopiMineNarcotics'
)

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'tests/TestMigrationOutputSnapshot.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Migration output snapshot behavior test failed.' }

$previousRecovery = Read-MigrationRecoveryRecord -RecoveryRecordPath $recoveryRecordPath -WorkspaceRoot $repo `
    -WorkspaceRevision $workspaceRevision -ExpectedRelativePaths $snapshotPaths
if ($previousRecovery) {
    Write-Warning "Recovering build outputs left by an interrupted migration gate from $($previousRecovery.TemporaryRoot)."
    Stop-MigrationGradleDaemon -GradleHome $isolatedGradleHome -UserHome $previousRecovery.UserHome -WorkspaceRoot $repo
    $recoveryRevision = Get-MigrationWorkspaceRevision -WorkspaceRoot $repo
    if (-not [string]::Equals($recoveryRevision, $workspaceRevision, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Workspace HEAD changed while validating interrupted recovery ($workspaceRevision -> $recoveryRevision); automatic restoration was withheld."
    }
    if (-not (Test-MigrationOutputSnapshotMatchesWorkspace -Snapshot $previousRecovery.Snapshot)) {
        $preservedState = Preserve-MigrationOutputStateBeforeRecovery -WorkspaceRoot $repo `
            -WorkspaceHash $workspaceHash -RecoveryStateDirectory $recoveryStateDirectory `
            -WorkspaceRevision $workspaceRevision -PreviousSnapshot $previousRecovery.Snapshot
        Write-Warning "Workspace outputs had changed since the interrupted gate snapshot; their current state is preserved at $($preservedState.ManifestPath)."
    }
    Restore-MigrationOutputSnapshot -Snapshot $previousRecovery.Snapshot
    Remove-Item -LiteralPath $recoveryRecordPath -Force -ErrorAction Stop
    try {
        Remove-MigrationTemporaryRoot -TemporaryBase $previousRecovery.TemporaryBase -TemporaryRoot $previousRecovery.TemporaryRoot
    } catch {
        Write-Warning "Recovered outputs, but could not remove the old temporary root: $($_.Exception.Message)"
    }
    Write-Host 'Recovered and restored the previous interrupted Java gate before starting a new run.'
}

$tempName = 'copimine-java-plugin-ci-{0}-{1}' -f $PID, [guid]::NewGuid().ToString('N')
$tempRoot = Join-Path $tempBase $tempName
New-Item -ItemType Directory -Path $tempRoot -ErrorAction Stop | Out-Null
$gateFailed = $false
try {
$diagnosticDirectory = Join-Path $repo (Join-Path 'artifacts/end-rift-diagnostics/java-plugin-ci' $tempName)
New-Item -ItemType Directory -Path $diagnosticDirectory -Force | Out-Null
$mavenRepository = Join-Path $tempRoot 'm2-repository'
$compileLockPath = Join-Path $repo 'tools/minecraft-26.3/java-plugin-compile-dependencies.lock.json'
$compileLock = Get-Content -LiteralPath $compileLockPath -Raw | ConvertFrom-Json
if ($compileLock.schemaVersion -ne 1 -or $compileLock.artifactRepository -ne 'https://repo.papermc.io/repository/maven-public' -or -not $compileLock.dependencies) {
    throw "Invalid Java compile dependency lock: $compileLockPath"
}
$lockedDependencies = @($compileLock.dependencies)
if ($lockedDependencies.Count -ne 18) { throw "Unexpected Java compile dependency count: $($lockedDependencies.Count)" }

Set-Location -LiteralPath $repo

$paperBase = 'https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.1-R0.1-SNAPSHOT'
$paperBuild = '1.21.1-R0.1-20250328.161643-128'
$paperJar = Join-Path $tempRoot "paper-api-$paperBuild.jar"
Save-PinnedArtifact -Uri "$paperBase/paper-api-$paperBuild.jar" -Destination $paperJar -Algorithm SHA256 -ExpectedHash 'b8df3e7f2739e21072a5263e41b307bd30cfa8d8f72258ce27973167f8ad07c0' -ExpectedSize 2344886 -MaximumBytes 2344886

$pluginDir = Join-Path $repo 'minecraft/server/plugins'
$placeholderCandidate = Join-Path $tempRoot 'PlaceholderAPI-2.12.3.jar'
Save-PinnedArtifact -Uri 'https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar' -Destination $placeholderCandidate -Algorithm SHA256 -ExpectedHash 'fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437' -ExpectedSize 1160690 -MaximumBytes 1160690
$env:PAPER_PLACEHOLDER_API_JAR = $placeholderCandidate

$verifiedClasspath = [Collections.Generic.List[string]]::new()
$mavenRepositoryFullPath = [IO.Path]::GetFullPath($mavenRepository).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
foreach ($locked in $lockedDependencies) {
    if ($locked.path -notmatch '^[A-Za-z0-9._+/-]+\.jar$' -or $locked.path.Split('/') -contains '..' -or
        $locked.size -le 0 -or $locked.sha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Invalid Java compile dependency lock entry: $($locked.path)"
    }
    $expectedPath = [IO.Path]::GetFullPath((Join-Path $mavenRepository $locked.path.Replace('/', [IO.Path]::DirectorySeparatorChar)))
    if (-not $expectedPath.StartsWith($mavenRepositoryFullPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Java compile dependency path escapes its isolated repository: $($locked.path)"
    }
    $encodedPath = ($locked.path.Split('/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
    $artifactUri = $compileLock.artifactRepository.TrimEnd('/') + '/' + $encodedPath
    Save-PinnedArtifact -Uri $artifactUri -Destination $expectedPath -Algorithm SHA256 -ExpectedHash $locked.sha256 -ExpectedSize $locked.size -MaximumBytes $locked.size
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
$compileDependencyJars = $verifiedClasspath.ToArray()
$compileClasspath = [string]::Join([IO.Path]::PathSeparator, $compileDependencyJars)
if ($compileClasspath.Length -gt 16000) {
    throw "Pinned Paper compile classpath exceeds the safe Windows command-line budget: $($compileClasspath.Length) characters."
}
$pinnedTestDependencies = @(
    @{ RelativePath = 'org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar'; Url = 'https://repo.papermc.io/repository/maven-public/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar'; Sha256 = '1467931448a0817696ae2805b7b8b20bfb082652bf9c4efaed528930dc49389b'; Size = 334352 },
    @{ RelativePath = 'com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar'; Url = 'https://repo.papermc.io/repository/maven-public/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar'; Sha256 = 'bc65dea7cfd9e4dacf8419d8af0e741655857d27885bb35d943d7187fc3a8fce'; Size = 3041591 }
)
foreach ($dependency in $pinnedTestDependencies) {
    $dependencyPath = Join-Path $mavenRepository $dependency.RelativePath
    Save-PinnedArtifact -Uri $dependency.Url -Destination $dependencyPath -Algorithm SHA256 -ExpectedHash $dependency.Sha256 -ExpectedSize $dependency.Size -MaximumBytes $dependency.Size
}
$serverPluginLock = Get-Content -LiteralPath (Join-Path $repo 'tools/minecraft-26.3/server-plugins.lock.json') -Raw | ConvertFrom-Json
$lockedVoicechat = @($serverPluginLock.modules + $serverPluginLock.unchangedBaseline | Where-Object { $_.pluginName -eq 'voicechat' })
if ($lockedVoicechat.Count -ne 1 -or -not $lockedVoicechat[0].url -or -not $lockedVoicechat[0].size -or -not $lockedVoicechat[0].sha256) {
    throw 'The migration server-plugin lock must contain one fully pinned Voice Chat API artifact.'
}
$voicechatFilename = [string]$lockedVoicechat[0].filename
if ($voicechatFilename -notmatch '^[A-Za-z0-9][A-Za-z0-9._+-]*\.jar$' -or
    [IO.Path]::GetFileName($voicechatFilename) -cne $voicechatFilename) {
    throw 'The migration Voice Chat artifact filename must be a simple JAR filename.'
}
$voicechatCandidate = [IO.Path]::GetFullPath((Join-Path $tempRoot $voicechatFilename))
$tempRootPrefix = [IO.Path]::GetFullPath($tempRoot).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $voicechatCandidate.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The migration Voice Chat artifact destination escapes the temporary directory.'
}
Save-PinnedArtifact -Uri $lockedVoicechat[0].url -Destination $voicechatCandidate -Algorithm SHA256 -ExpectedHash $lockedVoicechat[0].sha256 -ExpectedSize $lockedVoicechat[0].size -MaximumBytes $lockedVoicechat[0].size -StopAt $tempRoot
$env:PAPER_VOICECHAT_API_JAR = $voicechatCandidate
$env:COPIMINE_MAVEN_REPOSITORY = $mavenRepository
$env:PAPER_COMPILE_DEPS = $compileClasspath
$env:PAPER_API_JAR = $paperJar

$outputSnapshot = $null
$isolatedGradleUserHome = Join-Path $tempRoot 'gradle-user-home'
if (-not $KeepBuildOutputs) {
    $outputSnapshot = New-MigrationOutputSnapshot -WorkspaceRoot $repo -SnapshotDirectory (Join-Path $tempRoot 'workspace-output-snapshot') -RelativePaths $snapshotPaths
    Write-MigrationRecoveryRecord -RecoveryRecordPath $recoveryRecordPath -WorkspaceRoot $repo -TemporaryRoot $tempRoot `
        -TemporaryBase $tempBase -ManifestPath $outputSnapshot.ManifestPath -WorkspaceRevision $workspaceRevision
    Write-Host 'Local build outputs and plugin data will be restored to their pre-gate state.'
}

try {
$env:GRADLE_USER_HOME = $isolatedGradleUserHome
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTEST_ADDOPTS = (($env:PYTEST_ADDOPTS + ' -p no:cacheprovider').Trim())
foreach ($relativeDirectory in @(
    'copimine-world-core',
    'copimine-economy-core',
    'copimine-election-core',
    'copimine-admin-plugin',
    'copimine-artifacts',
    'copimine-end-event',
    'copimine-narcotics',
    'minecraft/server/plugins/AuthEffects'
)) {
    $buildScript = Join-Path (Join-Path $repo $relativeDirectory) 'build-plugin.ps1'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $buildScript
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $relativeDirectory" }
}

$ackTestSource = Join-Path $repo 'tests/ClientVisualAckStatusPolicyTest.java'
$ackTestBuild = Join-Path $tempRoot 'client-visual-ack-test'
$ackPluginClasses = Join-Path $repo 'copimine-narcotics/build/classes'
if (-not (Test-Path -LiteralPath $ackPluginClasses -PathType Container)) {
    throw "Client visual ACK policy classes are missing after the Narcotics build: $ackPluginClasses"
}
New-Item -ItemType Directory -Path $ackTestBuild -Force | Out-Null
$javac = Join-Path $jdk 'bin\javac.exe'
& $javac -proc:none -encoding UTF-8 -cp $ackPluginClasses -d $ackTestBuild $ackTestSource
if ($LASTEXITCODE -ne 0) { throw 'Client visual ACK status policy test compilation failed.' }
$ackTestClasspath = @($ackTestBuild, $ackPluginClasses) -join [IO.Path]::PathSeparator
& $java -cp $ackTestClasspath ClientVisualAckStatusPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Client visual ACK status policy test failed.' }

$orphanRefundTestSource = Join-Path $repo 'tests/OrphanShopTransferRecoveryStateTest.java'
$orphanRefundTestBuild = Join-Path $tempRoot 'orphan-shop-transfer-recovery-test'
$artifactsPluginClasses = Join-Path $repo 'copimine-artifacts/build/classes'
if (-not (Test-Path -LiteralPath $artifactsPluginClasses -PathType Container)) {
    throw "Artifacts plugin classes are missing after the plugin build: $artifactsPluginClasses"
}
New-Item -ItemType Directory -Path $orphanRefundTestBuild -Force | Out-Null
& $javac -proc:none -encoding UTF-8 -cp $artifactsPluginClasses -d $orphanRefundTestBuild $orphanRefundTestSource
if ($LASTEXITCODE -ne 0) { throw 'Artifacts orphan-transfer recovery state test compilation failed.' }
$orphanRefundTestClasspath = @($orphanRefundTestBuild, $artifactsPluginClasses) -join [IO.Path]::PathSeparator
& $java -cp $orphanRefundTestClasspath OrphanShopTransferRecoveryStateTest
if ($LASTEXITCODE -ne 0) { throw 'Artifacts orphan-transfer recovery state test failed.' }

$orphanRunGateTestSource = Join-Path $repo 'tests/OrphanTransferReconciliationRunGateTest.java'
$orphanRunGateTestBuild = Join-Path $tempRoot 'orphan-transfer-reconciliation-run-gate-test'
New-Item -ItemType Directory -Path $orphanRunGateTestBuild -Force | Out-Null
& $javac -proc:none -encoding UTF-8 -cp $artifactsPluginClasses -d $orphanRunGateTestBuild $orphanRunGateTestSource
if ($LASTEXITCODE -ne 0) { throw 'Artifacts orphan-transfer reconciliation run gate test compilation failed.' }
$orphanRunGateTestClasspath = @($orphanRunGateTestBuild, $artifactsPluginClasses) -join [IO.Path]::PathSeparator
& $java -cp $orphanRunGateTestClasspath OrphanTransferReconciliationRunGateTest
if ($LASTEXITCODE -ne 0) { throw 'Artifacts orphan-transfer reconciliation run gate test failed.' }

$shopRecoveryGuardTestSource = Join-Path $repo 'tests/ArtifactShopPurchaseRecoveryGuardTest.java'
$shopRecoveryGuardTestBuild = Join-Path $tempRoot 'artifact-shop-recovery-guard-test'
New-Item -ItemType Directory -Path $shopRecoveryGuardTestBuild -Force | Out-Null
& $javac -proc:none -encoding UTF-8 -cp $artifactsPluginClasses -d $shopRecoveryGuardTestBuild $shopRecoveryGuardTestSource
if ($LASTEXITCODE -ne 0) { throw 'Artifacts shop recovery guard test compilation failed.' }
$shopRecoveryGuardTestClasspath = @($shopRecoveryGuardTestBuild, $artifactsPluginClasses) -join [IO.Path]::PathSeparator
& $java -cp $shopRecoveryGuardTestClasspath ArtifactShopPurchaseRecoveryGuardTest
if ($LASTEXITCODE -ne 0) { throw 'Artifacts shop recovery guard test failed.' }

$revenuePolicyTestSource = Join-Path $repo 'tests/ArtifactRevenuePayoutPolicyTest.java'
$revenuePolicyTestBuild = Join-Path $tempRoot 'artifact-revenue-payout-policy-test'
New-Item -ItemType Directory -Path $revenuePolicyTestBuild -Force | Out-Null
& $javac -proc:none -encoding UTF-8 -cp $artifactsPluginClasses -d $revenuePolicyTestBuild $revenuePolicyTestSource
if ($LASTEXITCODE -ne 0) { throw 'Artifacts revenue payout policy test compilation failed.' }
$revenuePolicyTestClasspath = @($revenuePolicyTestBuild, $artifactsPluginClasses) -join [IO.Path]::PathSeparator
& $java -cp $revenuePolicyTestClasspath ArtifactRevenuePayoutPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Artifacts revenue payout policy test failed.' }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'CopiMineClient/build-client.ps1')
if ($LASTEXITCODE -ne 0) { throw 'CopiMineClient build failed.' }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'resourcepacks/build-resourcepack.ps1') -SkipServerProperties
if ($LASTEXITCODE -ne 0) { throw 'Resource pack build failed.' }
$resourcePack = Join-Path $repo 'resourcepacks/build/CopiMineResourcePack.zip'
$resourcePackSha1 = Join-Path $repo 'resourcepacks/build/CopiMineResourcePack.sha1'
if (-not (Test-Path -LiteralPath $resourcePack -PathType Leaf)) { throw "Resource pack archive was not created: $resourcePack" }
if (-not (Test-Path -LiteralPath $resourcePackSha1 -PathType Leaf)) { throw "Resource-pack digest sidecar was not created: $resourcePackSha1" }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'tests/PrepareCopiMinePluginFixtures.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Pinned third-party plugin fixtures failed verification.' }
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'tests/RunCopiMineValidators.ps1')
if ($LASTEXITCODE -ne 0) { throw 'CopiMine source and release validators failed.' }

$poseGeneratorCheck = Join-Path $repo 'copimine-end-event/tools/GenerateBossAnimationPoses.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $poseGeneratorCheck -Check
if ($LASTEXITCODE -ne 0) { throw 'Authored boss pose generator parity check failed.' }

$modpackBuild = Join-Path $repo 'scripts/thirdparty/build_modpack.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $modpackBuild -SyncBuiltClient
if ($LASTEXITCODE -ne 0) { throw 'Modpack build with the migrated client failed.' }

$endEventBuild = Join-Path $repo 'copimine-end-event/build-plugin.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $endEventBuild -SyncServerConfig
if ($LASTEXITCODE -ne 0) { throw 'End Event server configuration synchronization failed.' }

$eventLog = Join-Path $diagnosticDirectory 'end-rift-event-gate.log'
$eventStdoutLog = $eventLog + '.stdout'
$eventStderrLog = $eventLog + '.stderr'
$gateExitCode = 0
try {
    $eventRunnerPath = Join-Path $repo 'tests/RunEndRiftEventChecks.ps1'
    $eventRunnerArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $eventRunnerPath), '-SkipBuilds')
    $eventProcess = Start-Process -FilePath 'powershell.exe' -ArgumentList $eventRunnerArguments -WorkingDirectory $repo `
        -RedirectStandardOutput $eventStdoutLog -RedirectStandardError $eventStderrLog -WindowStyle Hidden -PassThru
    $eventProcessHandle = $eventProcess.Handle
    if ($eventProcessHandle -eq [IntPtr]::Zero) { throw 'The End Rift runner did not expose a process handle.' }
    $eventReadCounts = @{}
    $eventReadCounts[$eventStdoutLog] = 0
    $eventReadCounts[$eventStderrLog] = 0
    $eventOutputPaths = @($eventStdoutLog, $eventStderrLog)
    while (-not $eventProcess.HasExited) {
        foreach ($outputPath in $eventOutputPaths) {
            if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) { continue }
            try { $lines = @(Get-Content -LiteralPath $outputPath -ErrorAction Stop) } catch { continue }
            for ($lineIndex = [int]$eventReadCounts[$outputPath]; $lineIndex -lt $lines.Count; $lineIndex++) {
                Write-Host $lines[$lineIndex]
            }
            $eventReadCounts[$outputPath] = $lines.Count
        }
        [void]$eventProcess.WaitForExit(500)
    }
    $eventProcess.WaitForExit()
    foreach ($outputPath in $eventOutputPaths) {
        if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) { continue }
        try { $lines = @(Get-Content -LiteralPath $outputPath -ErrorAction Stop) } catch { $lines = @() }
        for ($lineIndex = [int]$eventReadCounts[$outputPath]; $lineIndex -lt $lines.Count; $lineIndex++) {
            Write-Host $lines[$lineIndex]
        }
        $eventReadCounts[$outputPath] = $lines.Count
    }
    $combinedEventLog = [Collections.Generic.List[string]]::new()
    foreach ($outputPath in $eventOutputPaths) {
        $combinedEventLog.Add(('=== {0} ===' -f [IO.Path]::GetExtension($outputPath).TrimStart('.')))
        if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
            foreach ($line in Get-Content -LiteralPath $outputPath -ErrorAction Stop) { $combinedEventLog.Add([string]$line) }
        }
    }
    [IO.File]::WriteAllLines($eventLog, $combinedEventLog.ToArray(), [Text.UTF8Encoding]::new($false))
    $eventProcess.Refresh()
    $childExitCode = $eventProcess.ExitCode
    if ($null -eq $childExitCode) { throw 'End Rift runner exited without a readable process code.' }
    $gateExitCode = [int]$childExitCode
    Write-Host "End Rift child process exit code: $gateExitCode"
} catch {
    $gateExitCode = 1
    Write-Host $_
}
if ($gateExitCode -ne 0) {
    $detail = if (Test-Path -LiteralPath $eventLog -PathType Leaf) {
        ((Get-Content -LiteralPath $eventLog -Tail 30) -join ' ') -replace '\s+', ' '
    } else {
        'The End Rift gate produced no captured output.'
    }
    if ($detail.Length -gt 4000) { $detail = $detail.Substring($detail.Length - 4000) }
    if ($env:GITHUB_ACTIONS) {
        $detail = $detail.Replace('%', '%25').Replace("`r", '%0D').Replace("`n", '%0A')
        Write-Output "::error title=End Rift gate detail::$detail"
    }
    throw "End Rift event gate failed with exit code $gateExitCode. Details: $detail"
}

Write-Host 'Java plugin CI gate passed with Temurin 21.0.12.1.'
} finally {
    Stop-MigrationGradleDaemon -GradleHome $isolatedGradleHome -UserHome $isolatedGradleUserHome -WorkspaceRoot $repo
    if ($outputSnapshot) {
        $restoreRevision = Get-MigrationWorkspaceRevision -WorkspaceRoot $repo
        if (-not [string]::Equals($restoreRevision, $workspaceRevision, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Workspace HEAD changed during the Java gate ($workspaceRevision -> $restoreRevision); output restoration was withheld and recovery data was preserved."
        }
        Restore-MigrationOutputSnapshot -Snapshot $outputSnapshot
        Remove-Item -LiteralPath $recoveryRecordPath -Force -ErrorAction Stop
        Write-Host 'Restored all pre-existing local build outputs and plugin data.'
    }
}
} catch {
    $gateFailed = $true
    throw
} finally {
    $preserveTemporaryRoot = $false
    if (Test-Path -LiteralPath $recoveryRecordPath -PathType Leaf) {
        try {
            $activeRecord = Get-Content -LiteralPath $recoveryRecordPath -Raw | ConvertFrom-Json
            $preserveTemporaryRoot = [string]::Equals([IO.Path]::GetFullPath([string]$activeRecord.temporaryRoot),
                [IO.Path]::GetFullPath($tempRoot), [StringComparison]::OrdinalIgnoreCase)
        } catch {
            $preserveTemporaryRoot = $true
        }
    }
    if ($preserveTemporaryRoot) {
        Write-Warning "Preserving $tempRoot because its recovery record remains at $recoveryRecordPath."
    } elseif ($gateFailed) {
        Write-Warning "Preserving failed gate inputs and diagnostics at $tempRoot."
    } else {
        try {
            Remove-MigrationTemporaryRoot -TemporaryBase $tempBase -TemporaryRoot $tempRoot
        } catch {
            Write-Warning "Could not remove temporary migration gate data at $tempRoot`: $($_.Exception.Message)"
        }
    }
}
} finally {
    foreach ($name in $gateEnvironmentNames) {
        $snapshot = $gateEnvironmentSnapshot[$name]
        if ($snapshot.WasSet) {
            [Environment]::SetEnvironmentVariable($name, $snapshot.Value, [EnvironmentVariableTarget]::Process)
        } else {
            [Environment]::SetEnvironmentVariable($name, $null, [EnvironmentVariableTarget]::Process)
        }
    }
    if ($gateLockStream) { $gateLockStream.Dispose() }
}
