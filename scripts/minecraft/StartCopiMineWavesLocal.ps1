[CmdletBinding()]
param(
    [switch]$Doctor,
    [string]$RepositoryRoot,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$root = if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
} else {
    (Resolve-Path -LiteralPath $RepositoryRoot).Path
}
$expectedRuntime = Join-Path $root 'local-runtime/end-rift-server-26.3'
$runtime = (Resolve-Path -LiteralPath $expectedRuntime).Path
if (-not [string]::Equals($runtime, $expectedRuntime, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Canonical runtime resolved outside its owned directory: $runtime"
}

function Assert-Artifact {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][long]$Size,
        [Parameter(Mandatory)][string]$Hash,
        [Parameter(Mandatory)][ValidateSet('SHA256', 'SHA512')][string]$Algorithm,
        [Parameter(Mandatory)][string]$Label
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label is missing: $Path"
    }
    $item = Get-Item -LiteralPath $Path
    if ($item.Length -ne $Size) {
        throw "$Label size mismatch: expected $Size, found $($item.Length) at $Path. Rebuild and install through the official installer."
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
    if ($actual -ne $Hash.ToLowerInvariant()) {
        throw "$Label hash mismatch: expected $Hash, found $actual at $Path. Refusing to start a stale or altered artifact."
    }
    Write-Output ("{0}: OK {1} {2} bytes" -f $Label, $Algorithm.ToLowerInvariant(), $Size)
}

$profile = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw |
    ConvertFrom-Json
$lock = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/server-plugins.lock.json') -Raw |
    ConvertFrom-Json
$endEventCandidate = $null
foreach ($baseline in @($lock.unchangedBaseline)) {
    $candidate = $baseline.candidate
    if ($null -ne $candidate -and $candidate.filename -eq 'CopiMineEndEvent-26.3.jar') {
        $endEventCandidate = $candidate
        break
    }
}
if ($null -eq $endEventCandidate) {
    throw 'CopiMineEndEvent candidate is not pinned in the 26.3 server-plugin lock.'
}

$paperFilename = 'paper-' + $profile.minecraftVersion + '-' + $profile.paper.build + '.jar'
$paperPath = Join-Path $root ('build/minecraft-26.3/server/' + $paperFilename)
$paperRuntimePath = Join-Path $runtime $paperFilename
$endEventPath = Join-Path $runtime 'plugins/CopiMineEndEvent.jar'
$clientPath = Join-Path $root ('build/minecraft-26.3/client/libs/' + $profile.clientArtifact.filename)
$packPath = Join-Path $root 'build/minecraft-26.3/CopiMineResourcePack-26.3.zip'

Write-Output "CopiMine Waves 26.3 canonical runtime: $runtime"
Assert-Artifact -Path $endEventPath -Size $endEventCandidate.size -Hash $endEventCandidate.sha256 -Algorithm SHA256 -Label 'Installed CopiMineEndEvent'
Assert-Artifact -Path $paperPath -Size $profile.paper.size -Hash $profile.paper.sha256 -Algorithm SHA256 -Label 'Paper 26.3'
Assert-Artifact -Path $paperRuntimePath -Size $profile.paper.size -Hash $profile.paper.sha256 -Algorithm SHA256 -Label 'Runtime Paper 26.3'
Assert-Artifact -Path $clientPath -Size $profile.clientArtifact.size -Hash $profile.clientArtifact.sha512 -Algorithm SHA512 -Label 'CopiMineClient 0.1.3+26.3'
$packSize = if (Test-Path -LiteralPath $packPath -PathType Leaf) { (Get-Item -LiteralPath $packPath).Length } else { 0 }
Assert-Artifact -Path $packPath -Size $packSize -Hash $profile.resourcePackMigration.candidateSha256 -Algorithm SHA256 -Label 'CopiMineResourcePack-26.3'

$propertiesPath = Join-Path $runtime 'server.properties'
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Canonical runtime server.properties is missing: $propertiesPath"
}
$properties = @{}
foreach ($line in [IO.File]::ReadAllLines($propertiesPath)) {
    if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $properties[$matches[1]] = $matches[2] }
}
if ($properties['server-ip'] -cne '127.0.0.1') { throw 'Canonical runtime must bind server-ip=127.0.0.1.' }
if ($properties['enable-rcon'] -cne 'false') { throw 'Canonical runtime must keep RCON disabled.' }
$port = [int]$properties['server-port']
if ($port -lt 1 -or $port -gt 65535) { throw 'Canonical runtime server-port is invalid.' }

$server = Join-Path $root 'scripts/minecraft/StartMigrationTestServer.ps1'

$logDirectory = Join-Path $root 'local-runtime/logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logPath = Join-Path $logDirectory ("copimine-waves-{0}.log" -f $stamp)

Write-Output ("Address: 127.0.0.1:{0}   Minecraft/Paper: {1}/{2}   Client: {3}   Pack: {4}" -f
    $port, $profile.minecraftVersion, $profile.paper.build, $profile.clientArtifact.version,
    (Split-Path -Leaf $packPath))
Write-Output "Launcher log: $logPath"
if ($Doctor) {
    Write-Output 'Doctor mode: validating Java, database identity, port ownership, and plugin profile without starting Paper.'
} else {
    Write-Output 'Waiting for Paper to load. Keep this console open; stop with Ctrl+C or the server console.'
}

$transcriptStarted = $false
try {
    Start-Transcript -Path $logPath -Append | Out-Null
    $transcriptStarted = $true
} catch {
    Write-Warning "Transcript logging is unavailable: $($_.Exception.Message)"
}
try {
    $childArguments = @()
    if ($Doctor) { $childArguments += '-ValidateOnly' }
    if ($JavaHome) { $childArguments += @('-JavaHome', $JavaHome) }
    # Run the starter in its own host process: in-process invocation can stall
    # while the isolated PostgreSQL cluster finishes recovery on Windows.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $server @childArguments
    $exitCode = $LASTEXITCODE
} finally {
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}
if ($exitCode -ne 0) {
    throw "Paper or its preflight failed with exit code $exitCode. See $logPath"
}
Write-Output 'Server stopped cleanly.'