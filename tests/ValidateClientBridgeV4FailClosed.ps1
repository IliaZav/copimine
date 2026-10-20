[CmdletBinding()]
param(
    [string]$GradleExecutable,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$ContractOnly
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$keyLoaderTestPath = Join-Path $root 'tools\minecraft-26.3\server-bridge-tests\src\test\java\me\copimine\clientbridge\BridgePrivateKeyLoaderTest.java'
$clientProtocolTestPath = Join-Path $root 'tools\minecraft-26.3\client\src\test\java\me\copimine\client\ClientBridgeProtocolV4Test.java'
$signingPolicyTestPath = Join-Path $root 'tools\minecraft-26.3\client\src\test\java\me\copimine\protocol\BridgeSigningPolicyTest.java'
$clientProtocolPath = Join-Path $root 'tools\minecraft-26.3\client\src\main\java\me\copimine\client\ClientBridgeProtocol.java'

function Require-File([string]$Path, [string]$Description) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing $Description."
    }
}

function Require-Pattern([string]$Text, [string]$Pattern, [string]$Description) {
    if ($Text -notmatch $Pattern) {
        throw "Fail-closed validation is missing $Description."
    }
}

Require-File $keyLoaderTestPath 'the Paper bridge key-loader test suite'
Require-File $clientProtocolTestPath 'the client v4 authentication test suite'
Require-File $signingPolicyTestPath 'the signing-policy test suite'
Require-File $clientProtocolPath 'the client bridge protocol source'

$keyLoaderTests = Get-Content -LiteralPath $keyLoaderTestPath -Raw -Encoding UTF8
$clientProtocolTests = Get-Content -LiteralPath $clientProtocolTestPath -Raw -Encoding UTF8
$signingPolicyTests = Get-Content -LiteralPath $signingPolicyTestPath -Raw -Encoding UTF8
$clientProtocol = Get-Content -LiteralPath $clientProtocolPath -Raw -Encoding UTF8

# The key-loader regression tests create temporary Ed25519 keypairs and exercise
# the real loader. They must not obtain signer material from machine configuration.
Require-Pattern $keyLoaderTests 'KeyPairGenerator\.getInstance\("Ed25519"\)' 'ephemeral Ed25519 test keys'
Require-Pattern $keyLoaderTests 'loadsOnlyAKeyThatMatchesTheExpectedPublicKeyAndReturnsSignedEnvelopeBytes' 'trusted-key signing coverage'
Require-Pattern $keyLoaderTests 'rejectsMissingEmptyMalformedOversizedAndMismatchedKeys' 'missing, malformed, and mismatched-key coverage'
if ($keyLoaderTests -match 'System\.getenv\s*\(|System\.getProperty\s*\(|Environment\.GetEnvironmentVariable\s*\(') {
    throw 'The key-loader tests must use only their generated temporary fixtures.'
}

Require-Pattern $clientProtocolTests 'wrongKeyWrongNonceAndModifiedRawBodyDoNotRetireAnEstablishedStream' 'client rejection coverage for untrusted or modified envelopes'
Require-Pattern $signingPolicyTests 'rejectsV3DomainSignaturesWithoutTryingALegacyFallback' 'v3 signature-domain fallback rejection coverage'

# V3 mutation handling is intentionally checked as a source contract: the current
# receiver is wired to Fabric networking and has no isolated runtime seam. The
# protected v4 mutation tests remain executable Java unit tests below.
$v3Registration = 'ClientPlayNetworking.registerGlobalReceiver(BridgePayload.ID,'
$v4Registration = 'ClientPlayNetworking.registerGlobalReceiver(BridgeV4Payload.ID,'
$v3Start = $clientProtocol.IndexOf($v3Registration, [StringComparison]::Ordinal)
$v4Start = $clientProtocol.IndexOf($v4Registration, [StringComparison]::Ordinal)
if ($v3Start -lt 0 -or $v4Start -le $v3Start) {
    throw 'Could not isolate the legacy and v4 networking receivers for the v3 mutation contract check.'
}
$v3Receiver = $clientProtocol.Substring($v3Start, $v4Start - $v3Start)
Require-Pattern $v3Receiver 'payload\.messageType\(\)\.startsWith\(TYPE_END_EVENT_PREFIX\)[\s\S]*?return;' 'an unconditional v3 End Event mutation rejection'
Require-Pattern $v3Receiver 'case\s+TYPE_VISUAL_START,\s*TYPE_VISUAL_STOP,\s*TYPE_VISUAL_CLEAR_ALL,\s*TYPE_PRISONER_ABILITY_REQUEST\s*->\s*CopiMineClientLogger\.warnFromServer' 'v3 visual/prisoner mutation rejection'
if ($v3Receiver -match '\b(?:applyEndEventPayload|applyV4Mutation|applyAuthenticatedRemoteVisual|clearV4Sender|clearEndEventState|resetEndEventConnection|clearAuthenticatedRemoteVisuals)\s*\(') {
    throw 'The legacy v3 receiver must not reach a protected mutation or cleanup handler.'
}
if ($v3Receiver -match '\b(?:registeredVisualManager|END_EVENT_STATE|END_EVENT_WORLD_VFX|END_EVENT_BLACK_FOG|END_EVENT_PLAYER_VISUALS|PRISONER_HUD)\b') {
    throw 'The legacy v3 receiver must not access protected client visual or End Event state directly.'
}
Require-Pattern $clientProtocol 'registerGlobalReceiver\(BridgeV4Payload\.ID,[\s\S]*?BRIDGE_V4\.onEnvelope\(' 'a separate v4 receiver guarded by envelope validation'

Write-Host '[CONTRACT] Generated-key test fixtures and no-v3-mutation source contract passed.'

if ($ContractOnly) {
    Write-Host 'FAIL_CLOSED_CONTRACT_PASS (unit tests were not run in ContractOnly mode).'
    return
}

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

function Invoke-GradleTest([string]$Label, [string[]]$Arguments) {
    Write-Host "== $Label =="
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $global:LASTEXITCODE = 0
        & $script:gradlePath @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode."
    }
}

$versions = Get-LockedBuildVersions
$script:javaHomePath = Resolve-JavaHome $JavaHome
$javaBin = Join-Path $script:javaHomePath 'bin'
$javaExecutable = Join-Path $javaBin 'java.exe'
$hadJavaHome = Test-Path Env:JAVA_HOME
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
try {
    $env:JAVA_HOME = $script:javaHomePath
    $env:PATH = "$javaBin;$previousPath"
    $script:gradlePath = Resolve-GradleExecutable $GradleExecutable
    Assert-PinnedBuildRuntime $script:gradlePath $javaExecutable $versions.Gradle $versions.Java
    Write-Host "[RUNTIME] Locked Gradle $($versions.Gradle) and Java $($versions.Java) validated."

    $minecraftProject = Join-Path $root 'tools\minecraft-26.3'
    $clientProject = Join-Path $minecraftProject 'client'
    $serverTests = @(
        'me.copimine.clientbridge.BridgePrivateKeyLoaderTest.loadsOnlyAKeyThatMatchesTheExpectedPublicKeyAndReturnsSignedEnvelopeBytes',
        'me.copimine.clientbridge.BridgePrivateKeyLoaderTest.rejectsMissingEmptyMalformedOversizedAndMismatchedKeys'
    )
    $serverArgs = @('-p', $minecraftProject, 'bridgeServerTest', '--no-daemon', '--rerun-tasks', '--console=plain')
    foreach ($testName in $serverTests) {
        $serverArgs += @('--tests', $testName)
    }
    Invoke-GradleTest 'Paper key-loader fail-closed unit tests (temporary generated keys)' $serverArgs

    $clientArgs = @(
        '-p', $clientProject,
        'test',
        '--no-daemon',
        '--rerun-tasks',
        '--console=plain',
        '--tests', 'me.copimine.client.ClientBridgeProtocolV4Test.wrongKeyWrongNonceAndModifiedRawBodyDoNotRetireAnEstablishedStream',
        '--tests', 'me.copimine.protocol.BridgeSigningPolicyTest.rejectsV3DomainSignaturesWithoutTryingALegacyFallback'
    )
    Invoke-GradleTest 'Client v4 authentication rejection unit test' $clientArgs

    Write-Host 'FAIL_CLOSED_TESTS_PASS (Java unit tests and a v3 source contract only; no Paper server or Minecraft client was launched).'
} finally {
    if ($hadJavaHome) {
        $env:JAVA_HOME = $previousJavaHome
    } else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:PATH = $previousPath
}
