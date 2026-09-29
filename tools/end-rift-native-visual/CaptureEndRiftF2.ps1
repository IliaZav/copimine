[CmdletBinding()]
param(
    [string]$Config = '',
    [string]$OutputDirectory,
    [ValidateRange(1, 30)][int]$TimeoutSeconds = 10
)

# Reproducible, local-only End Rift showroom capture. This deliberately requires
# one connected Minecraft client: screenshots come from Minecraft's own F2 path.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($scriptDirectory)) { throw 'Could not locate CaptureEndRiftF2.ps1.' }
if ([string]::IsNullOrWhiteSpace($Config)) { $Config = Join-Path $scriptDirectory 'config.json' }
Import-Module (Join-Path $scriptDirectory 'lib\EndRift.NativeCapture.psm1') -Force

$c = Read-EndRiftConfig -Path $Config
$worktree = (Resolve-Path -LiteralPath ([string]$c.Worktree)).Path
$expectedWorktree = (Resolve-Path -LiteralPath (Join-Path $scriptDirectory '..\..')).Path
if (-not [string]::Equals($worktree, $expectedWorktree, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refused config Worktree outside this helper's checkout: $worktree"
}
if ([string]$c.ExpectedBranch -ne 'codex/end-rift-event') {
    throw "Refused unexpected ExpectedBranch: $($c.ExpectedBranch)"
}
$branch = (& git -C $worktree branch --show-current 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "Refused Git branch '$branch'; expected codex/end-rift-event."
}
$gitHead = (& git -C $worktree rev-parse HEAD 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $gitHead -notmatch '^[0-9a-f]{40}$') {
    throw 'Could not resolve a valid Git HEAD for the evidence manifest.'
}

$expectedServerDir = (Resolve-Path -LiteralPath (Join-Path $worktree 'local-runtime\end-rift-server')).Path
$configuredServerDir = (Resolve-Path -LiteralPath ([string]$c.ServerDir)).Path
if (-not [string]::Equals($configuredServerDir, $expectedServerDir, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refused server outside this worktree's local-runtime/end-rift-server: $configuredServerDir"
}
if ([string]$c.ServerAddress -ne '127.0.0.1:25566' -or [int]$c.RconPort -ne 25576) {
    throw "Refused server endpoint $($c.ServerAddress) RCON $($c.RconPort); expected 127.0.0.1:25566 / 25576."
}
$expectedRconScript = (Resolve-Path -LiteralPath (Join-Path $worktree 'tests\InvokeEndRiftLocalRcon.ps1')).Path
$configuredRconScript = (Resolve-Path -LiteralPath ([string]$c.RconScript)).Path
if (-not [string]::Equals($configuredRconScript, $expectedRconScript, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refused an RCON helper other than tests/InvokeEndRiftLocalRcon.ps1.'
}

$sourceConfigPath = Join-Path $worktree 'copimine-end-event\config.yml'
$installedConfigPath = Join-Path $configuredServerDir 'plugins\CopiMineEndEvent\config.yml'
$propertiesPath = Join-Path $configuredServerDir 'server.properties'
foreach ($path in @($sourceConfigPath, $installedConfigPath, $propertiesPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required local server file is missing: $path" }
}
foreach ($path in @($sourceConfigPath, $installedConfigPath)) {
    if ((Get-Content -LiteralPath $path -Raw -Encoding UTF8) -notmatch '(?m)^environment:\s*local\s*$') {
        throw "Refused non-local End Rift configuration: $path"
    }
}
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath -Encoding UTF8) {
    if ($line -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576' -or
    $properties['server-ip'] -ne '127.0.0.1' -or $properties['rcon.ip'] -ne '127.0.0.1' -or
    $properties['enable-rcon'] -ne 'true') {
    throw 'Refused: isolated local server.properties must bind server/RCON to 127.0.0.1 on ports 25566/25576 with RCON enabled.'
}

$sourceServerJar = Join-Path $worktree 'copimine-end-event\CopiMineEndEvent.jar'
$loadedServerJar = Join-Path $configuredServerDir 'plugins\CopiMineEndEvent.jar'
$sourceServerHash = (Get-FileHash -LiteralPath $sourceServerJar -Algorithm SHA256).Hash.ToLowerInvariant()
$loadedServerHash = (Get-FileHash -LiteralPath $loadedServerJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sourceServerHash -ne $loadedServerHash) { throw 'Local server JAR does not match the current End Rift build.' }
$serverListeners = @(Get-NetTCPConnection -State Listen -LocalPort 25566 -ErrorAction Stop)
if ($serverListeners.Count -ne 1) { throw "Expected one local server listener on 25566, found $($serverListeners.Count)." }
$serverProcess = Get-Process -Id ([int]$serverListeners[0].OwningProcess) -ErrorAction Stop
if ($serverProcess.ProcessName -notmatch '^java$') { throw 'Port 25566 is not owned by the expected local Java server.' }
if ((Get-Item -LiteralPath $loadedServerJar).LastWriteTimeUtc -gt $serverProcess.StartTime.ToUniversalTime().AddSeconds(1)) {
    throw 'The local End Rift JAR was written after this server process started.'
}

$clientJar = (Resolve-Path -LiteralPath ([string]$c.ClientJarPath)).Path
$sourceClientJar = Join-Path $worktree 'CopiMineClient\build\libs\CopiMineClient-0.1.1.jar'
if (-not (Test-Path -LiteralPath $sourceClientJar -PathType Leaf)) { throw "Current client build is missing: $sourceClientJar" }
$clientHash = (Get-FileHash -LiteralPath $clientJar -Algorithm SHA256).Hash.ToLowerInvariant()
$sourceClientHash = (Get-FileHash -LiteralPath $sourceClientJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($clientHash -ne $sourceClientHash) { throw 'Installed Minecraft client JAR does not match the current CopiMineClient build.' }
$expectedClientGameDir = (Resolve-Path -LiteralPath ([string]$c.ClientGameDirectory)).Path
$window = Get-MinecraftWindow -TitleRegex ([string]$c.MinecraftTitleRegex)
$clientProcess = Get-Process -Id ([int]$window.ProcessId) -ErrorAction Stop
if ($clientProcess.ProcessName -notmatch '^javaw?$' -or
    $clientProcess.StartTime.ToUniversalTime().AddSeconds(1) -lt
        (Get-Item -LiteralPath $clientJar).LastWriteTimeUtc) {
    throw 'Minecraft must be running with the installed CopiMineClient JAR loaded.'
}
$processInfo = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $([int]$window.ProcessId)" -ErrorAction Stop
if ($null -eq $processInfo) { throw 'Could not verify the active Minecraft process arguments.' }
$gameDirMatch = [Regex]::Match([string]$processInfo.CommandLine,
    '(?i)(?:^|\s)--gameDir(?:\s+|=)(?:"(?<quoted>[^"]+)"|(?<plain>[^\s]+))')
if (-not $gameDirMatch.Success) { throw 'Could not find --gameDir in the active Minecraft process arguments.' }
$activeGameDir = if ($gameDirMatch.Groups['quoted'].Success) {
    $gameDirMatch.Groups['quoted'].Value
} else {
    $gameDirMatch.Groups['plain'].Value
}
$activeGameDir = (Resolve-Path -LiteralPath $activeGameDir -ErrorAction Stop).Path
if (-not [string]::Equals($activeGameDir, $expectedClientGameDir, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Minecraft is running from a different game directory: $activeGameDir"
}

function Invoke-Local([string]$Command) {
    return (Invoke-EndRiftRcon -Config $c -CommandText $Command)
}

function ConvertTo-Plain([string]$Text) {
    return [Regex]::Replace($Text, '\u00A7.', '')
}

$listReply = ConvertTo-Plain (Invoke-Local 'list')
$onlineNames = New-Object System.Collections.Generic.List[string]
foreach ($match in [Regex]::Matches($listReply, '(?m):\s*([A-Za-z0-9_]+(?:\s*,\s*[A-Za-z0-9_]+)*)\s*$')) {
    foreach ($name in ($match.Groups[1].Value -split '\s*,\s*')) {
        if (-not $onlineNames.Contains($name)) { $onlineNames.Add($name) }
    }
}
$cameraPlayer = [string]$c.CameraPlayer
if ([string]::IsNullOrWhiteSpace($cameraPlayer)) {
    throw 'Set CameraPlayer in the local visual config to the intended Minecraft account before capture.'
}
if (-not $onlineNames.Contains($cameraPlayer)) {
    throw "Configured CameraPlayer is not connected to the isolated local server: $cameraPlayer"
}
if ($cameraPlayer -notmatch '^[A-Za-z0-9_]{1,16}$') { throw 'CameraPlayer contains an invalid Minecraft name.' }

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = New-EndRiftEvidenceDirectory -Config $c -Suffix 'f2-showroom'
} elseif (-not [IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $worktree $OutputDirectory
}
$artifactRoot = (Resolve-Path -LiteralPath (Join-Path $worktree 'artifacts')).Path.TrimEnd('\') + '\'
$outputFullPath = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $outputFullPath.StartsWith($artifactRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "F2 evidence must stay under this checkout's artifacts directory: $outputFullPath"
}
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$plannedEvidenceFiles = @(
    'showroom-overview.png',
    'elite-enderman.png',
    'elite-skeleton.png',
    'elite-spider.png',
    'tentacles-ready.png',
    'manifest.json'
)
foreach ($name in $plannedEvidenceFiles) {
    if (Test-Path -LiteralPath (Join-Path $OutputDirectory $name)) {
        throw "Refused to overwrite existing evidence file: $(Join-Path $OutputDirectory $name)"
    }
}

function Format-Coordinate([double]$Value) {
    return $Value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Get-Vector([string]$Reply) {
    $match = [Regex]::Match($Reply, '\[\s*(-?[0-9]+(?:\.[0-9]+)?)(?:d)?\s*,\s*(-?[0-9]+(?:\.[0-9]+)?)(?:d)?\s*,\s*(-?[0-9]+(?:\.[0-9]+)?)(?:d)?\s*\]')
    if (-not $match.Success) { throw "Could not read server coordinates: $Reply" }
    return [pscustomobject]@{
        X = [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
        Y = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
        Z = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
    }
}

function Move-Camera([double]$X, [double]$Y, [double]$Z, [int]$Yaw, [int]$Pitch) {
    $command = 'minecraft:execute in minecraft:overworld run minecraft:tp {0} {1} {2} {3} {4} {5}' -f $cameraPlayer,
        (Format-Coordinate $X), (Format-Coordinate $Y), (Format-Coordinate $Z), $Yaw, $Pitch
    $null = Invoke-Local $command
    Start-Sleep -Milliseconds 350
    $position = Get-Vector (Invoke-Local "data get entity $cameraPlayer Pos")
    if ([Math]::Abs($position.X - $X) -gt 1.0 -or
        [Math]::Abs($position.Y - $Y) -gt 1.0 -or
        [Math]::Abs($position.Z - $Z) -gt 1.0) {
        throw "Camera teleport did not reach the requested position: $($position | ConvertTo-Json -Compress)"
    }
    Start-Sleep -Milliseconds 1800
}

$showroomReply = ConvertTo-Plain (Invoke-Local 'cmend test showroom')
if ($showroomReply -notmatch '(?i)SHOWROOM') { throw "Showroom setup did not report success: $showroomReply" }
$statusReply = ConvertTo-Plain (Invoke-Local 'cmend status')
$coreMatch = [Regex]::Match($statusReply, 'core=\S+\s+(-?[0-9]+),(-?[0-9]+),(-?[0-9]+)')
if (-not $coreMatch.Success) { throw "Could not read End Rift core coordinates: $statusReply" }
$coreX = [double]$coreMatch.Groups[1].Value
$coreY = [double]$coreMatch.Groups[2].Value
$coreZ = [double]$coreMatch.Groups[3].Value

$initialPerspective = [string]$c.InitialPerspective
$perspectiveIndex = @{
    'first-person' = 0
    'third-person-back' = 1
    'third-person-front' = 2
}
if (-not $perspectiveIndex.ContainsKey($initialPerspective)) {
    throw "InitialPerspective must be first-person, third-person-back, or third-person-front."
}
if ($null -eq $c.HudVisibleAtStart) {
    throw 'HudVisibleAtStart must describe whether the Minecraft HUD is visible before capture.'
}
$hudVisibleAtStart = [bool]$c.HudVisibleAtStart
$f5ToFirstPerson = (3 - [int]$perspectiveIndex[$initialPerspective]) % 3

$visualReply = ConvertTo-Plain (Invoke-Local 'cmend test visuals mobs')
$expectedVisuals = @(
    [pscustomobject]@{ Id = 'END_RIFT_ELITE_V1'; Texture = 'end_rift_elite.png'; File = 'elite-enderman'; CameraDistance = 5.5; Pitch = 1 },
    [pscustomobject]@{ Id = 'END_RIFT_ELITE_SKELETON_V1'; Texture = 'end_rift_elite_skeleton.png'; File = 'elite-skeleton'; CameraDistance = 4.8; Pitch = 8 },
    [pscustomobject]@{ Id = 'END_RIFT_ELITE_SPIDER_V1'; Texture = 'end_rift_elite_spider.png'; File = 'elite-spider'; CameraDistance = 3.8; Pitch = 14 }
)
$eliteEntities = @{}
foreach ($expected in $expectedVisuals) {
    $pattern = '(?m)MOB_VISUAL uuid=(?<uuid>[0-9a-f-]+) role=ELITE clientVisual=' +
        [Regex]::Escape($expected.Id) + ' boundViewers=(?<viewers>[0-9]+) resource=.*' +
        [Regex]::Escape($expected.Texture) + '\s*$'
    $match = [Regex]::Match($visualReply, $pattern)
    if (-not $match.Success) { throw "Expected bound elite visual $($expected.Id) -> $($expected.Texture) was not reported.`n$visualReply" }
    if ([int]$match.Groups['viewers'].Value -lt 1) { throw "No connected viewer is bound to $($expected.Id)." }
    $eliteEntities[$expected.Id] = $match.Groups['uuid'].Value
}

$f2Script = Join-Path $scriptDirectory '07_CaptureViaMinecraftF2.ps1'
$captures = New-Object System.Collections.Generic.List[object]
function Save-F2([string]$Name) {
    $path = Join-Path $OutputDirectory ($Name + '.png')
    if (Test-Path -LiteralPath $path) { throw "Refused to overwrite existing F2 evidence: $path" }
    $output = & $f2Script -Config $Config -Output $path -Label $Name -TimeoutSeconds $TimeoutSeconds 2>&1 | Out-String
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Minecraft F2 capture failed for $Name`: $output"
    }
    $file = Get-Item -LiteralPath $path
    if ($file.Length -lt 4096) { throw "F2 screenshot is unexpectedly small for $Name ($($file.Length) bytes)." }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $captures.Add([pscustomobject]@{
        name = $Name
        file = [IO.Path]::GetFileName($path)
        bytes = $file.Length
        sha256 = $hash
        capturedAt = $file.LastWriteTimeUtc.ToString('o')
        method = 'Minecraft F2'
    })
    Write-Output "F2_CAPTURE name=$Name bytes=$($file.Length) sha256=$hash"
}

$dayTimeReply = ConvertTo-Plain (Invoke-Local 'minecraft:time query daytime')
$dayTimeMatch = [Regex]::Match($dayTimeReply, '(?i)\b(?:time is|daytime is)\s+([0-9]+)\b')
if (-not $dayTimeMatch.Success) { throw "Could not read the local server's current daytime: $dayTimeReply" }
$originalDayTime = [int]$dayTimeMatch.Groups[1].Value
$timeChanged = $false
$nightVisionApplied = $false
$hudToggled = $false
$viewTogglesApplied = 0

try {
    $null = Invoke-Local 'minecraft:time set midnight'
    $timeChanged = $true
    $null = Invoke-Local "minecraft:effect give $cameraPlayer minecraft:night_vision 300 0 true"
    $nightVisionApplied = $true

    for ($i = 0; $i -lt $f5ToFirstPerson; $i++) {
        Send-WindowKeyChord -Window $window -Chord 'F5'
        $viewTogglesApplied++
    }
    if ($hudVisibleAtStart) {
        Send-WindowKeyChord -Window $window -Chord 'F1'
        $hudToggled = $true
    }

    # Keep the overview inside the arena. The player stands at floor level;
    # teleporting above it lets gravity move the camera before RCON can verify it.
    Move-Camera $coreX $coreY ($coreZ - 16.0) 0 0
    Save-F2 'showroom-overview'

    foreach ($expected in $expectedVisuals) {
        $entityUuid = [string]$eliteEntities[$expected.Id]
        $entityPosition = Get-Vector (Invoke-Local "data get entity $entityUuid Pos")
        $targetPosition = 'minecraft:execute in minecraft:overworld run minecraft:tp {0} {1} {2} {3} 180 0' -f $entityUuid, (Format-Coordinate $entityPosition.X), (Format-Coordinate $entityPosition.Y), (Format-Coordinate $entityPosition.Z)
        $null = Invoke-Local ('data merge entity {0} {{NoAI:1b,CustomNameVisible:0b}}' -f $entityUuid)
        $null = Invoke-Local $targetPosition
        Move-Camera $entityPosition.X $entityPosition.Y ($entityPosition.Z - $expected.CameraDistance) 0 $expected.Pitch
        Save-F2 $expected.File
    }

    # This records the two tentacles' READY pose. Dynamic attack/hit behavior
    # remains verified separately by the live gameplay probe and Java tests.
    Move-Camera ($coreX + 10.0) $coreY $coreZ -90 0
    Save-F2 'tentacles-ready'
} finally {
    if ($hudToggled) {
        try { Send-WindowKeyChord -Window $window -Chord 'F1' } catch { Write-Warning "Could not restore Minecraft HUD state: $_" }
    }
    $restoreViewToggles = (3 - ($viewTogglesApplied % 3)) % 3
    for ($i = 0; $i -lt $restoreViewToggles; $i++) {
        try { Send-WindowKeyChord -Window $window -Chord 'F5' } catch { Write-Warning "Could not restore Minecraft perspective: $_"; break }
    }
    if ($nightVisionApplied) {
        try { $null = Invoke-Local "minecraft:effect clear $cameraPlayer minecraft:night_vision" } catch { Write-Warning "Could not clear temporary Night Vision: $_" }
    }
    if ($timeChanged) {
        try { $null = Invoke-Local "minecraft:time set $originalDayTime" } catch { Write-Warning "Could not restore the local world's time: $_" }
    }
}

$manifestPath = Join-Path $OutputDirectory 'manifest.json'
$manifest = [ordered]@{
    status = 'CAPTURED_REQUIRES_VISUAL_REVIEW'
    capturedAt = (Get-Date).ToUniversalTime().ToString('o')
    branch = $branch
    gitHead = $gitHead
    server = [ordered]@{
        address = '127.0.0.1:25566'
        rconPort = 25576
        environment = 'local'
        serverJarSha256 = $loadedServerHash
    }
    client = [ordered]@{
        gameDirectory = $expectedClientGameDir
        processId = [int]$window.ProcessId
        clientJarSha256 = $clientHash
    }
    scene = [ordered]@{
        setup = 'cmend test showroom'
        phase = 'static local End Rift showroom'
        core = [ordered]@{ x = $coreX; y = $coreY; z = $coreZ }
        eliteMappings = @($expectedVisuals | ForEach-Object {
            [ordered]@{ visualId = $_.Id; texture = $_.Texture; entityUuid = [string]$eliteEntities[$_.Id] }
        })
    }
    screenshots = @($captures.ToArray())
    visualReview = 'PENDING: inspect each original PNG in Minecraft rendering before marking visually verified.'
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
$manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "END_RIFT_F2_EVIDENCE directory=$OutputDirectory manifestSha256=$manifestHash screenshots=$($captures.Count)"
