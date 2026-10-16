[CmdletBinding()]
param(
    [string]$GradleExecutable,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipEndRiftGate
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Resolve-GradleExecutable([string]$Requested) {
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        if (Test-Path -LiteralPath $Requested -PathType Leaf) {
            return (Resolve-Path -LiteralPath $Requested).Path
        }
        $command = Get-Command $Requested -ErrorAction SilentlyContinue
        if ($null -ne $command -and $command.CommandType -eq 'Application') {
            return $command.Source
        }
        throw 'The requested Gradle executable was not found.'
    }

    foreach ($name in @('gradle.bat', 'gradle')) {
        $command = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $command) {
            return $command.Source
        }
    }
    throw 'The Gradle version from profile.lock.json was not found. Pass -GradleExecutable or add the pinned distribution to PATH.'
}

function Resolve-JavaHome([string]$Requested) {
    if ([string]::IsNullOrWhiteSpace($Requested) -or -not (Test-Path -LiteralPath $Requested -PathType Container)) {
        throw 'Pass -JavaHome pointing to the Java runtime locked by profile.lock.json.'
    }
    $resolved = (Resolve-Path -LiteralPath $Requested).Path
    $javaExecutable = Join-Path $resolved 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw '-JavaHome must contain bin\java.exe.'
    }
    return $resolved
}

function Invoke-NativeCommandCapture([string]$Executable, [string[]]$Arguments) {
    # Windows PowerShell 5.1 promotes native stderr to NativeCommandError records.
    # java -version writes to stderr even on success, so capture it without inheriting
    # the script's Stop preference and retain the process exit code separately.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $global:LASTEXITCODE = 0
        $output = (& $Executable @Arguments 2>&1 | Out-String)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return @{ Output = $output; ExitCode = $exitCode }
}

function Get-LockedBuildVersions {
    $lockPath = Join-Path $root 'tools\minecraft-26.3\profile.lock.json'
    $lock = Get-Content -LiteralPath $lockPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $gradleVersion = [string]$lock.gradle.version
    $javaVersion = [string]$lock.buildJavaVersion
    if ([string]::IsNullOrWhiteSpace($gradleVersion) -or [string]::IsNullOrWhiteSpace($javaVersion)) {
        throw 'profile.lock.json must specify gradle.version and buildJavaVersion.'
    }
    return @{ Gradle = $gradleVersion; Java = $javaVersion }
}

function Assert-PinnedBuildRuntime([string]$Executable, [string]$JavaExecutable, [string]$ExpectedGradle, [string]$ExpectedJava) {
    $javaResult = Invoke-NativeCommandCapture $JavaExecutable @('-version')
    $javaPattern = 'version\s+"' + [regex]::Escape($ExpectedJava) + '"'
    if ($javaResult.ExitCode -ne 0 -or $javaResult.Output -notmatch $javaPattern) {
        throw 'The selected -JavaHome does not match buildJavaVersion in profile.lock.json.'
    }

    $gradleResult = Invoke-NativeCommandCapture $Executable @('--version')
    $gradlePattern = '(?m)^Gradle\s+' + [regex]::Escape($ExpectedGradle) + '\s*$'
    $runtimePattern = '(?m)^(?:Launcher\s+)?JVM:\s+' + [regex]::Escape($ExpectedJava) + '(?:\s|$)'
    if ($gradleResult.ExitCode -ne 0 -or $gradleResult.Output -notmatch $gradlePattern) {
        throw 'The selected Gradle executable does not match gradle.version in profile.lock.json.'
    }
    if ($gradleResult.Output -notmatch $runtimePattern) {
        throw 'Gradle is not running under the exact Java version from -JavaHome.'
    }
}

function Invoke-CheckedProcess([string]$Label, [string]$Executable, [string[]]$Arguments) {
    Write-Host "== $Label =="
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $global:LASTEXITCODE = 0
        & $Executable @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode."
    }
    $script:completedSteps.Add($Label)
}

$script:completedSteps = [System.Collections.Generic.List[string]]::new()
$versions = Get-LockedBuildVersions
$script:javaHomePath = Resolve-JavaHome $JavaHome
$javaBin = Join-Path $script:javaHomePath 'bin'
$javaExecutable = Join-Path $javaBin 'java.exe'
$hadJavaHome = Test-Path Env:JAVA_HOME
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$legacyClientJavaHome = $previousJavaHome
if (-not [string]::IsNullOrWhiteSpace($legacyClientJavaHome)) {
    $legacyClientJavaHome = [System.IO.Path]::GetFullPath($legacyClientJavaHome).TrimEnd([char[]]@('\', '/'))
}
if (-not $SkipEndRiftGate -and [string]::IsNullOrWhiteSpace($legacyClientJavaHome)) {
    $originalJavaCommand = Get-Command 'java.exe' -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $originalJavaCommand) {
        throw 'The integrated End Rift gate requires the original Java 21 runtime for CopiMineClient.'
    }
    $legacyClientJavaHome = Split-Path -Parent (Split-Path -Parent $originalJavaCommand.Source)
}
try {
    $env:JAVA_HOME = $script:javaHomePath
    $env:PATH = "$javaBin;$previousPath"
    $script:gradlePath = Resolve-GradleExecutable $GradleExecutable
    Assert-PinnedBuildRuntime $script:gradlePath $javaExecutable $versions.Gradle $versions.Java
    Write-Host "[RUNTIME] Locked Gradle $($versions.Gradle) and Java $($versions.Java) validated."

$windowsPowerShell = Join-Path $PSHOME 'powershell.exe'
if (-not (Test-Path -LiteralPath $windowsPowerShell -PathType Leaf)) {
    $windowsPowerShell = (Get-Command powershell.exe -CommandType Application -ErrorAction Stop).Source
}

$clientProject = Join-Path $root 'tools\minecraft-26.3\client'
$minecraftProject = Join-Path $root 'tools\minecraft-26.3'

Write-Host 'Client Bridge v4 automated regression harness: steps below are unit, contract, or compile checks.'
Write-Host 'The harness does not start Paper or connect a Minecraft client; live gameplay verification is reported separately.'

Invoke-CheckedProcess 'Fail-closed source contracts' $windowsPowerShell @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', (Join-Path $PSScriptRoot 'ValidateClientBridgeV4FailClosed.ps1'),
    '-GradleExecutable', $script:gradlePath,
    '-JavaHome', $script:javaHomePath,
    '-ContractOnly'
)

Invoke-CheckedProcess 'Full Minecraft 26.3 client and protocol JUnit suite' $script:gradlePath @(
    '-p', $clientProject,
    'test',
    '-Dorg.gradle.jvmargs=-Xmx384m',
    '--max-workers=1',
    '--no-daemon',
    '--rerun-tasks',
    '--console=plain'
)

Invoke-CheckedProcess 'Shared Paper bridge unit suite and pinned migration plugin JAR builds' $script:gradlePath @(
    '-p', $minecraftProject,
    'bridgeServerTest',
    'migrationPlugins',
    '-Dorg.gradle.jvmargs=-Xmx384m',
    '--max-workers=1',
    '--no-daemon',
    '--rerun-tasks',
    '--console=plain'
)

Invoke-CheckedProcess 'Narcotics v4 source contract guard' $windowsPowerShell @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', (Join-Path $PSScriptRoot 'ValidateCopiMineClientBridgeV4Narcotics.ps1')
)

Invoke-CheckedProcess 'End Event v4 source contract guard' $windowsPowerShell @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', (Join-Path $PSScriptRoot 'ValidateCopiMineEndEventBridgeV4.ps1')
)

if ($SkipEndRiftGate) {
    Write-Host '[SKIPPED] Full End Rift repository regression gate (-SkipEndRiftGate was supplied).'
} else {
    $buildRoot = Join-Path $root 'build'
    if (Test-Path -LiteralPath $buildRoot) {
        if (-not (Test-Path -LiteralPath $buildRoot -PathType Container)) {
            throw 'The ignored build path exists but is not a directory.'
        }
    }
    else {
        New-Item -ItemType Directory -Path $buildRoot | Out-Null
    }
    $baseTempPath = Join-Path $buildRoot ('p-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    while (Test-Path -LiteralPath $baseTempPath) {
        $baseTempPath = Join-Path $buildRoot ('p-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    }
    $hadPytestAddopts = Test-Path Env:PYTEST_ADDOPTS
    $previousPytestAddopts = $env:PYTEST_ADDOPTS
    try {
        $pytestPathArgument = $baseTempPath.Replace('\', '/')
        $env:PYTEST_ADDOPTS = "--basetemp='$pytestPathArgument'"
        $endRiftArguments = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass',
            '-File', (Join-Path $PSScriptRoot 'RunEndRiftEventChecks.ps1'),
            '-LegacyClientJavaHome', $legacyClientJavaHome
        )
        Invoke-CheckedProcess 'Full End Rift repository regression gate (all builds enabled)' $windowsPowerShell $endRiftArguments
    } finally {
        if ($hadPytestAddopts) {
            $env:PYTEST_ADDOPTS = $previousPytestAddopts
        } else {
            Remove-Item Env:PYTEST_ADDOPTS -ErrorAction SilentlyContinue
        }
    }
}

Write-Host ''
Write-Host 'CLIENT_BRIDGE_V4_AUTOMATED_GATE_PASS'
Write-Host 'Evidence classes: Java unit tests, source contract checks, and compile/JAR resource checks.'
Write-Host 'The Paper/JAR steps do not load a signer into a running server.'
Write-Host 'LIVE MINECRAFT: NOT RUN; nativeVerified=false until a real Paper server and Fabric client session are observed.'
Write-Host 'The signing-key tests use generated temporary Ed25519 fixtures; this runner does not access a deployed signing key.'
if ($SkipEndRiftGate) {
    Write-Host 'The End Rift regression gate was explicitly skipped; rerun without -SkipEndRiftGate for the full repository gate.'
}
} finally {
    if ($hadJavaHome) {
        $env:JAVA_HOME = $previousJavaHome
    } else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:PATH = $previousPath
}
