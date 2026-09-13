[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftBoundaryA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftBoundaryB',
  [ValidateRange(30, 300)]
  [int]$BotDurationSeconds = 120,
  [ValidateRange(10, 180)]
  [int]$TimeoutSeconds = 60
)

# Local-only runtime probe for the two objectives that need physical arena
# geometry. It uses disposable test waves, never edits the saved arena by
# design, and verifies that Wave 7's BARRIER cells are removed by cleanup.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$processes = [System.Collections.Generic.List[object]]::new()

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "Local RCON failed: $CommandText $result" }
  return $result.Trim()
}

function Read-Log { return [string](Get-Content -LiteralPath $paperLog -Raw) }
function Log-Length { return [int64](Read-Log).Length }
function Log-Tail([int64]$Offset) {
  $text = Read-Log
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}

function Wait-Log {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Log-Tail $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'."
}

function Get-Core([string]$Status) {
  $match = [Regex]::Match(($Status -replace '\u00A7.', ''), 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core is missing from status: $Status" }
  return [int[]]@([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Start-Bot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][int[]]$Core)
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $output = Join-Path $runtimeRoot ($Name + '-wave6-wave7.log')
  $error = Join-Path $runtimeRoot ($Name + '-wave6-wave7.err.log')
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 0.5D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 900'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $output -RedirectStandardError $error -WindowStyle Hidden -PassThru
  $processes.Add($process)
}

function Wait-BotsOnline([string[]]$Names) {
  for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($Names | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Boundary probe players did not join: $($Names -join ', ')"
}

function Assert-BarrierBlock([int[]]$Core, [int]$FloorY, [int64]$AfterOffset) {
  # With two chambers the physical separator is a diameter. Probe the center
  # lane and both sides of its three-block collision cross-section.
  $points = @(
    [pscustomobject]@{ X = $Core[0] + 5; Y = $FloorY + 1; Z = $Core[2] },
    [pscustomobject]@{ X = $Core[0] + 5; Y = $FloorY + 1; Z = $Core[2] + 1 },
    [pscustomobject]@{ X = $Core[0] + 5; Y = $FloorY + 1; Z = $Core[2] - 1 },
    [pscustomobject]@{ X = $Core[0] + 10; Y = $FloorY + 1; Z = $Core[2] }
  )
  foreach ($point in $points) {
    $probe = 'END_RIFT_BARRIER_PROBE_PASS'
    $null = Invoke-LocalRcon ("execute if block $($point.X) $($point.Y) $($point.Z) minecraft:barrier run say $probe")
    Start-Sleep -Milliseconds 150
    if ((Log-Tail $AfterOffset) -match $probe) {
      return "$($point.X),$($point.Y),$($point.Z)"
    }
  }
  throw 'Wave 7 created no probeable physical BARRIER cell.'
}

$names = @($FirstBotName, $SecondBotName)
if ($names[0] -eq $names[1]) { throw 'Boundary probe players must be distinct.' }
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused Git branch '$branch'."
}
$properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Refused: boundary probe requires isolated local ports.'
}

$core = Get-Core (Invoke-LocalRcon 'cmend status')
$floorY = $core[1]
try {
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  foreach ($name in $names) { Start-Bot -Name $name -Core $core }
  Wait-BotsOnline -Names $names
  foreach ($name in $names) {
    $null = Invoke-LocalRcon ("gamemode survival $name")
    $null = Invoke-LocalRcon ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon ("tp $name $($core[0] + 6) $($core[1]) $($core[2] + 0.5)")
  }
  Start-Sleep -Seconds 2

  $wave6Offset = Log-Length
  $null = Invoke-LocalRcon 'cmend test wave 6'
  Wait-Log -AfterOffset $wave6Offset -Pattern 'WAVE_TEST_STARTED.*wave=6\b' | Out-Null
  $ringLog = Wait-Log -AfterOffset $wave6Offset `
    -Pattern 'END_RIFT_RINGS_READY.*radii=6,11,16.*visual_points=64,80,96'
  $floorMatch = [Regex]::Match($ringLog, 'END_RIFT_RINGS_READY.*floor_y=(-?\d+)')
  if (-not $floorMatch.Success) { throw "Wave 6 did not expose the combat floor: $ringLog" }
  $floorY = [int]$floorMatch.Groups[1].Value
  $wave6Objective = Invoke-LocalRcon 'cmend debug objectives'
  $wave6Match = [Regex]::Match(($wave6Objective -replace '\u00A7.', ''), 'visuals=(\d+)')
  if (-not $wave6Match.Success -or [int]$wave6Match.Groups[1].Value -lt 100) {
    throw "Wave 6 did not expose all enlarged ring visuals: $wave6Objective"
  }
  Write-Output "LIVE_WAVE6_BOUNDARIES_PASS rings=3 radii=6,11,16 visual_displays=$($wave6Match.Groups[1].Value) visual_points=64,80,96 leash_policy=true"

  $null = Invoke-LocalRcon 'cmend wave clear'
  $wave7Offset = Log-Length
  $null = Invoke-LocalRcon 'cmend test wave 7'
  $barrierLog = Wait-Log -AfterOffset $wave7Offset `
    -Pattern 'END_RIFT_WAVE7_BARRIERS_READY.*cells=(\d+).*height=3.*collision=true.*journaled=true'
  $barrierMatch = [Regex]::Match($barrierLog, 'END_RIFT_WAVE7_BARRIERS_READY.*cells=(\d+)')
  if (-not $barrierMatch.Success -or [int]$barrierMatch.Groups[1].Value -le 0) {
    throw "Wave 7 barrier count was not positive: $barrierLog"
  }
  $wave7Objective = Invoke-LocalRcon 'cmend debug objectives'
  $wave7VisualMatch = [Regex]::Match(($wave7Objective -replace '\u00A7.', ''), 'visuals=(\d+)')
  if (-not $wave7VisualMatch.Success -or [int]$wave7VisualMatch.Groups[1].Value -le 0) {
    throw "Wave 7 did not expose barrier visuals: $wave7Objective"
  }
  $probePoint = Assert-BarrierBlock -Core $core -FloorY $floorY -AfterOffset $wave7Offset
  Write-Output "LIVE_WAVE7_BARRIERS_PASS chambers=2 cells=$($barrierMatch.Groups[1].Value) visual_displays=$($wave7VisualMatch.Groups[1].Value) barrier=$($probePoint -join ',') collision=true"

  $cleanupOffset = Log-Length
  $null = Invoke-LocalRcon 'cmend wave clear'
  Start-Sleep -Seconds 1
  $cleanupTail = Log-Tail $cleanupOffset
  if ($cleanupTail -match 'END_RIFT_BARRIER_PROBE_PASS') {
    throw 'Wave 7 barrier remained after /cmend wave clear.'
  }
  $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($status -notmatch 'event-mobs=\s*0' -or $status -notmatch 'rift-obelisks=0/6') {
    throw "Wave 7 cleanup left transient state: $status"
  }
  Write-Output 'LIVE_WAVE7_BARRIER_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0'
}
finally {
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { } }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.WaitForExit(5000) | Out-Null } catch { } }
  }
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
}
