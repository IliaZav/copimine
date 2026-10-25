[CmdletBinding()]
param(
    # Canonical runtime only; the manifest records arena identity, so a wrong
    # runtime/plugin state must fail here instead of being silently recorded.
    [string]$RuntimeDirectory,
    [string]$OutputPath,
    [ValidatePattern('^[A-Za-z0-9_.-]{1,40}$')][string]$Scenario = 'unlabelled',
    [ValidateRange(2, 20)][int]$RosterSize = 2,
    [ValidateSet('survival', 'creative', 'spectator')][string]$GameMode = 'survival',
    [string]$ClientVersion = '',
    [string]$Notes = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($RuntimeDirectory)) {
    $RuntimeDirectory = Join-Path $root 'local-runtime/end-rift-server-26.3'
}
$runtime = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$statePath = Join-Path $runtime 'plugins/CopiMineEndEvent/event-state.yml'
$layoutPath = Join-Path $runtime 'plugins/CopiMineEndEvent/event-layout.yml'
$propertiesPath = Join-Path $runtime 'server.properties'
foreach ($path in @($statePath, $layoutPath, $propertiesPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Run manifest requires the runtime event identity file: $path"
    }
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-FirstValue {
    # shortcut: flat key read for the plugin's own state files, replace with a
    # YAML parser if these files ever gain nested duplicates of these keys.
    param([string]$Text, [string]$Key, [string]$Pattern = '(.+)')
    $match = [regex]::Match($Text, "(?m)^\s*$([regex]::Escape($Key)):\s*$Pattern\s*$")
    if (-not $match.Success) { return $null }
    return $match.Groups[1].Value.Trim().Trim("'", '"')
}

function Get-Property {
    # server.properties uses `key=value`, unlike the plugin's YAML state files.
    param([string]$Text, [string]$Key)
    $match = [regex]::Match($Text, "(?m)^$([regex]::Escape($Key))=(.*?)\s*$")
    if (-not $match.Success) { return $null }
    return $match.Groups[1].Value.Trim()
}

$state = [IO.File]::ReadAllText($statePath)
$layout = [IO.File]::ReadAllText($layoutPath)
$properties = [IO.File]::ReadAllText($propertiesPath)
$eventWorld = Get-FirstValue -Text $state -Key 'world'
$coreX = Get-FirstValue -Text $state -Key 'x' -Pattern '(-?\d+)'
$coreY = Get-FirstValue -Text $state -Key 'y' -Pattern '(-?\d+)'
$coreZ = Get-FirstValue -Text $state -Key 'z' -Pattern '(-?\d+)'
if ([string]::IsNullOrWhiteSpace($eventWorld) -or $null -eq $coreX -or $null -eq $coreY -or $null -eq $coreZ) {
    throw "Run manifest cannot read core identity from $statePath"
}

$levelName = Get-Property -Text $properties -Key 'level-name'
$worldDirectory = Join-Path $runtime $levelName
if (-not (Test-Path -LiteralPath (Join-Path $worldDirectory 'level.dat') -PathType Leaf)) {
    throw "Configured level '$levelName' has no level.dat under $runtime"
}

$gitErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$dirty = @(& git -C $root status --porcelain=v1 -uall 2>$null)
$diffText = [string](& git -C $root diff --binary HEAD 2>$null | Out-String)
$branchName = ([string](& git -C $root branch --show-current 2>$null)).Trim()
$headSha = ([string](& git -C $root rev-parse HEAD 2>$null)).Trim()
$ErrorActionPreference = $gitErrorAction
if ([string]::IsNullOrWhiteSpace($headSha)) {
    throw "Run manifest cannot resolve the source revision at $root"
}
$diffBytes = [Text.Encoding]::UTF8.GetBytes($diffText)
$diffHash = [BitConverter]::ToString(
    [Security.Cryptography.SHA256]::Create().ComputeHash($diffBytes)).Replace('-', '').ToLowerInvariant()

$regionDirectory = Join-Path $worldDirectory 'dimensions/minecraft/overworld/region'
$regions = if (Test-Path -LiteralPath $regionDirectory -PathType Container) {
    @(Get-ChildItem -LiteralPath $regionDirectory -Filter '*.mca' -File)
} else { @() }

$clientLock = Join-Path $root 'tools/minecraft-26.3/profile.lock.json'
$clientArtifact = if (Test-Path -LiteralPath $clientLock -PathType Leaf) {
    (Get-Content -LiteralPath $clientLock -Raw | ConvertFrom-Json).clientArtifact
} else { $null }
$endEventJar = Join-Path $runtime 'plugins/CopiMineEndEvent.jar'

$manifest = [ordered]@{
    schemaVersion = 1
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
    scenario = $Scenario
    notes = $Notes
    roster = [ordered]@{ size = $RosterSize; gameMode = $GameMode }
    source = [ordered]@{
        branch = $branchName
        head = $headSha
        dirtyEntries = $dirty.Count
        dirtyDiffSha256 = $diffHash
    }
    runtime = [ordered]@{
        directory = $runtime
        levelName = $levelName
        levelSeed = Get-Property -Text $properties -Key 'level-seed'
        serverPort = Get-Property -Text $properties -Key 'server-port'
        rconEnabled = (Get-Property -Text $properties -Key 'enable-rcon') -eq 'true'
        onlineMode = (Get-Property -Text $properties -Key 'online-mode') -eq 'true'
        clientVersion = $ClientVersion
    }
    event = [ordered]@{
        eventId = Get-FirstValue -Text $state -Key 'event-id'
        generation = Get-FirstValue -Text $state -Key 'generation'
        phase = Get-FirstValue -Text $state -Key 'phase'
        requiredPlayers = Get-FirstValue -Text $state -Key 'required-players'
        core = [ordered]@{
            world = $eventWorld
            x = [int]$coreX
            y = [int]$coreY
            z = [int]$coreZ
            blockData = Get-FirstValue -Text $state -Key 'block-data'
        }
        arenaMin = @(
            [int](Get-FirstValue -Text $state -Key 'min-x'),
            [int](Get-FirstValue -Text $state -Key 'min-y'),
            [int](Get-FirstValue -Text $state -Key 'min-z'))
        arenaMax = @(
            [int](Get-FirstValue -Text $state -Key 'max-x'),
            [int](Get-FirstValue -Text $state -Key 'max-y'),
            [int](Get-FirstValue -Text $state -Key 'max-z'))
        gateStatus = Get-FirstValue -Text $layout -Key 'status'
    }
    world = [ordered]@{
        levelDatSha256 = Get-Sha256 -Path (Join-Path $worldDirectory 'level.dat')
        regionCount = $regions.Count
        regionBytes = [long](($regions | Measure-Object -Property Length -Sum).Sum)
    }
    artifacts = [ordered]@{
        installedEndEventJar = if (Test-Path -LiteralPath $endEventJar -PathType Leaf) {
            [ordered]@{
                sha256 = Get-Sha256 -Path $endEventJar
                size = (Get-Item -LiteralPath $endEventJar).Length
            }
        } else { $null }
        clientLockSha256 = if (Test-Path -LiteralPath $clientLock -PathType Leaf) {
            Get-Sha256 -Path $clientLock
        } else { $null }
        clientArtifact = if ($null -ne $clientArtifact) {
            [ordered]@{ size = $clientArtifact.size; sha512 = $clientArtifact.sha512 }
        } else { $null }
    }
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $root ("local-runtime/run-manifests/end-rift-{0}-{1}.json" -f
        (Get-Date -Format 'yyyyMMdd-HHmmss'), $Scenario)
}
$outputDirectory = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
$json = $manifest | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText($OutputPath, $json, (New-Object Text.UTF8Encoding($false)))

$check = Get-Content -LiteralPath $OutputPath -Raw | ConvertFrom-Json
if ($check.event.core.world -ne $eventWorld -or $check.event.core.x -ne [int]$coreX -or
    $check.runtime.levelName -ne $levelName -or [string]::IsNullOrWhiteSpace($check.source.head)) {
    throw "Run manifest round-trip check failed: $OutputPath"
}
Write-Output "Run manifest: $OutputPath"
Write-Output ("Arena: {0} core=({1},{2},{3}) bounds={4}..{5}" -f $eventWorld, $coreX, $coreY, $coreZ,
    ($manifest.event.arenaMin -join ','), ($manifest.event.arenaMax -join ','))
