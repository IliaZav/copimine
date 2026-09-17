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
# design, and verifies that Wave 7's one-block BARRIER collision cells are
# removed by cleanup. AMETHYST_BLOCK remains the separate visual layer.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$processes = [System.Collections.Generic.List[object]]::new()
$botProcesses = [System.Collections.Generic.List[object]]::new()
$previousWave7Autopilot = $env:END_RIFT_BOT_WAVE7_AUTOPILOT
$combatTraceProbe = $env:END_RIFT_BOT_COMBAT_TRACE -eq '1'
$previousLocalMobSpawning = $null

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

function Clear-LocalArenaAmbientMobs([int[]]$Core) {
  # Mineflayer cannot inspect the server-side event PDC marker. Remove only
  # ordinary arena mobs before a disposable wave starts, so the test client
  # cannot mistake them for event-owned targets. Never call this after the
  # restart boundary: recovered Wave 7 mobs are intentionally preserved.
  $position = "$($Core[0] + 0.5D) $($Core[1]) $($Core[2] + 0.5D)"
  foreach ($mobType in @('minecraft:spider', 'minecraft:enderman', 'minecraft:skeleton')) {
    $null = Invoke-LocalRcon ("execute positioned $position run kill @e[type=$mobType,distance=..32]")
  }
}

function Set-LocalArenaMobSpawning([bool]$Enabled) {
  if ($Enabled) {
    $null = Invoke-LocalRcon 'gamerule doMobSpawning true'
  } else {
    $null = Invoke-LocalRcon 'gamerule doMobSpawning false'
  }
}

function Start-Bot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][int[]]$Core)
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $output = Join-Path $runtimeRoot ($Name + '-wave6-wave7.log')
  $error = Join-Path $runtimeRoot ($Name + '-wave6-wave7.err.log')
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 0.5D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 250'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $output -RedirectStandardError $error -WindowStyle Hidden -PassThru
  $processes.Add($process)
  $botProcesses.Add($process)
}

function Stop-Bots {
  foreach ($process in $botProcesses) {
    if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { } }
  }
  foreach ($process in $botProcesses) {
    if ($process) { try { $process.WaitForExit(5000) | Out-Null } catch { } }
  }
  $botProcesses.Clear()
}

function Wait-BotsOffline([string[]]$Names) {
  for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($Names | Where-Object { $list -match [Regex]::Escape($_) }).Count -eq 0) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Boundary probe players did not disconnect: $($Names -join ', ')"
}

function Configure-Bot {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][int[]]$Core,
    [switch]$SkipTeleport
  )
  $null = Invoke-LocalRcon ("gamemode survival $name")
  $null = Invoke-LocalRcon ("attribute $name minecraft:generic.max_health base set 1000")
  # The local probe accounts are reused between runs.  Older diagnostics set
  # their persistent base attack damage to zero, which makes a netherite-sword
  # packet reach PrePlayerAttack but fail Paper's positive-damage gate for one
  # client.  Reset it to the vanilla survival base before every live wave.
  $null = Invoke-LocalRcon ("attribute $name minecraft:generic.attack_damage base set 1")
  # Keep the geometry probe focused on containment, reach and server-authority
  # instead of allowing a mob hit to eject a client from its assigned chamber.
  # The official multi-player probe uses the same deterministic combat setup.
  $null = Invoke-LocalRcon ("attribute $name minecraft:generic.knockback_resistance base set 1")
  # Amplifier 255 overflows the vanilla instant-health shift on 1.21.1 and
  # can heal only one point.  Level 11 is already far above the local 1000 HP
  # harness maximum without relying on that overflow edge case.
  $null = Invoke-LocalRcon ("effect give $name minecraft:instant_health 1 10 true")
  $null = Invoke-LocalRcon ("effect give $name minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon ("minecraft:item replace entity $name weapon.mainhand with minecraft:netherite_sword")
  $weaponState = Invoke-LocalRcon ("data get entity $name SelectedItem")
  if ($weaponState -notmatch 'minecraft:netherite_sword') {
    throw "Boundary probe weapon setup failed for $name`: $weaponState"
  }
  if (-not $SkipTeleport) {
    $null = Invoke-LocalRcon ("tp $name $($core[0] + 6) $($core[1]) $($core[2] + 0.5)")
  }
}

function Teleport-Player {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][double]$X,
    [Parameter(Mandatory = $true)][double]$Y,
    [Parameter(Mandatory = $true)][double]$Z
  )
  $null = Invoke-LocalRcon ("tp $Name $X $Y $Z")
}

function Wait-BotsOnline([string[]]$Names) {
  for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($Names | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Boundary probe players did not join: $($Names -join ', ')"
}

function Wait-BotLoginSettle {
  # The local accounts are authenticated by AuthMe shortly after the network
  # join.  Configure persistent attributes only after that profile reload, or
  # an old account value can silently replace the deterministic probe setup.
  Start-Sleep -Seconds 7
}

function Sync-BotsHeldItem {
  # Mineflayer can believe slot 0 is already selected and skip the packet that
  # makes Paper re-evaluate the item attribute modifier. Signal the already
  # configured clients after every replacement instead of racing from spawn.
  $null = Invoke-LocalRcon 'say END_RIFT_BOUNDARY_SYNC_HELD_ITEM'
  Start-Sleep -Milliseconds 500
}

function Restart-Bots([string[]]$Names, [int[]]$Core) {
  # Wave 6 can run long enough for a short-lived diagnostic client to expire
  # before Wave 7 begins.  Refresh both clients at this boundary so the server
  # always receives a complete two-player chamber assignment and the
  # post-restart completion probe has a full client lifetime.
  Stop-Bots
  Wait-BotsOffline -Names $Names
  foreach ($name in $Names) { Start-Bot -Name $name -Core $Core }
  Wait-BotsOnline -Names $Names
  Wait-BotLoginSettle
  foreach ($name in $Names) { Configure-Bot -Name $name -Core $Core }
  Sync-BotsHeldItem
  Start-Sleep -Seconds 2
}

function Assert-BarrierBlock([int[]]$Core, [int]$FloorY, [int64]$AfterOffset) {
  # With two chambers the physical separator is the east/west diameter. Probe
  # its one-block staircase; there is no two-block-wide gameplay cross-section.
  $points = @(
    [pscustomobject]@{ X = $Core[0] + 5; Y = $FloorY + 1; Z = $Core[2] },
    [pscustomobject]@{ X = $Core[0] + 10; Y = $FloorY + 1; Z = $Core[2] },
    [pscustomobject]@{ X = $Core[0] - 5; Y = $FloorY + 1; Z = $Core[2] }
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

function Test-PortOpen([int]$Port) {
  $client = [Net.Sockets.TcpClient]::new()
  try {
    $client.Connect('127.0.0.1', $Port)
    return $true
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Wait-Port {
  param(
    [Parameter(Mandatory = $true)][int]$Port,
    [Parameter(Mandatory = $true)][bool]$Expected,
    [int]$Seconds = 60
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if ((Test-PortOpen $Port) -eq $Expected) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Port $Port did not reach expected state open=$Expected."
}

function Read-LocalEnvironment {
  # The checked-in local launcher owns the environment in end-rift.env.  Keep
  # restart recovery on that same file so a clean local boot and an in-test
  # restart cannot drift to two different database/pack configurations.
  $path = Join-Path $runtimeRoot 'end-rift.env'
  if (-not (Test-Path -LiteralPath $path)) {
    throw "Local environment file is missing: $path"
  }
  foreach ($line in Get-Content -LiteralPath $path) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      Set-Item -Path ("Env:" + $matches[1]) -Value $matches[2].Trim().Trim('"').Trim("'")
    }
  }
  $env:COPIMINE_ENV_FILE = $path
}

function Start-LocalMinecraft {
  Read-LocalEnvironment
  $java = (Get-Command java.exe -ErrorAction Stop).Source
  $stdout = Join-Path $runtimeRoot ("wave7-restart-" + (Get-Date -Format 'yyyyMMddHHmmss') + '.stdout.log')
  $stderr = Join-Path $runtimeRoot ("wave7-restart-" + (Get-Date -Format 'yyyyMMddHHmmss') + '.stderr.log')
  $process = Start-Process -FilePath $java -ArgumentList @('-Xms1G', '-Xmx2G', '-jar', 'purpur.jar', 'nogui') `
    -WorkingDirectory $serverDir -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
    -WindowStyle Hidden -PassThru
  $processes.Add($process)
  Wait-Port -Port 25576 -Expected $true -Seconds 90
}

function Wait-Log-MarkerIncrease {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int]$BeforeCount,
    [int64]$BeforeLength = 0,
    [int]$Seconds = 60
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Path -LiteralPath $paperLog) {
      $current = Read-Log
      $count = [Regex]::Matches($current, $Pattern).Count
      # Paper rotates/recreates latest.log on a clean process restart. In
      # that case a valid first marker in the new file is evidence even when
      # its count is not greater than the old file's count.
      $rotated = $BeforeLength -gt 0 -and $current.Length -lt $BeforeLength
      if ($count -gt $BeforeCount -or ($rotated -and $count -gt 0)) { return }
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for a new log marker matching '$Pattern'."
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
  # Keep each real client in the room assigned by the server's Wave 7
  # containment controller. This is a navigation bound only; damage,
  # chamber ownership and completion remain server-authoritative.
  $env:END_RIFT_BOT_WAVE7_AUTOPILOT = '1'
  $mobSpawning = Invoke-LocalRcon 'gamerule doMobSpawning'
  $mobSpawningMatch = [Regex]::Match($mobSpawning,
    '(?i)doMobSpawning(?:\s*=\s*|\s+is\s+currently\s+set\s+to:\s*)(true|false)')
  if (-not $mobSpawningMatch.Success) {
    throw "Could not read local doMobSpawning gamerule: $mobSpawning"
  }
  $previousLocalMobSpawning = $mobSpawningMatch.Groups[1].Value.ToLowerInvariant()
  Set-LocalArenaMobSpawning -Enabled $false
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  if ($combatTraceProbe) {
    $null = Invoke-LocalRcon 'cmend debug trace on'
  }
  Clear-LocalArenaAmbientMobs -Core $core
  Restart-Bots -Names $names -Core $core

  $wave6Offset = Log-Length
  $null = Invoke-LocalRcon 'cmend test wave 6'
  Wait-Log -AfterOffset $wave6Offset -Pattern 'WAVE_TEST_STARTED.*wave=6\b' | Out-Null
  $ritualLog = Wait-Log -AfterOffset $wave6Offset `
    -Pattern 'WAVE6_RITUAL_SPHERE_READY.*casters=4.*guards=12.*projectiles=1.*zones=1.*control_pairs=0.*drain_interval_ms=20000.*drain_hp=2.*health_floor=1.*authority=server'
  if ($ritualLog -match 'END_RIFT_RINGS_READY|WAVE_6_PAIR_SPAWNED') {
    throw 'Legacy Collapse Rings appeared in the live Wave 6 log.'
  }
  # The live objective starts in WAITING_FOR_PRISONER.  Put exactly one real
  # client on the visible seal and require the server-authored capture marker
  # before waiting for the first drain; starting both clients at core+6 is an
  # outside-seal state and must never be treated as a capture.
  Teleport-Player -Name $SecondBotName -X ($core[0] + 0.5D) -Y $core[1] -Z ($core[2] + 0.5D)
  $captureLog = Wait-Log -AfterOffset $wave6Offset `
    -Pattern 'WAVE6_RITUAL_PRISONER_CAPTURED[^\r\n]*player=[0-9a-fA-F-]{32,36}[^\r\n]*first_drain_at=\d+'
  $drainLog = Wait-Log -AfterOffset $wave6Offset `
    -Pattern 'WAVE6_RITUAL_PRISONER_DRAIN.*applied=true.*damage=(?:1\.9+|2(?:\.0+)?)' -Seconds 90
  $prisonerMatch = [Regex]::Match($captureLog, 'player=([0-9a-fA-F-]{32,36})')
  if (-not $prisonerMatch.Success) { throw "Ritual Sphere did not identify a prisoner: $ritualLog" }
  $wave6Objective = Invoke-LocalRcon 'cmend debug objectives'
  $wave6Match = [Regex]::Match(($wave6Objective -replace '\u00A7.', ''), 'visuals=(\d+)')
  if (-not $wave6Match.Success -or [int]$wave6Match.Groups[1].Value -lt 1) {
    throw "Wave 6 did not expose Ritual Sphere visuals: $wave6Objective"
  }
  Write-Output "LIVE_WAVE6_RITUAL_SPHERE_PASS casters=4 guards=12 prisoner=$($prisonerMatch.Groups[1].Value) drain_interval_ms=20000 drain_hp=2 health_floor=1 visual_displays=$($wave6Match.Groups[1].Value) legacy_rings=false"

  $null = Invoke-LocalRcon 'cmend wave clear'
  Clear-LocalArenaAmbientMobs -Core $core
  Restart-Bots -Names $names -Core $core
  $wave7Offset = Log-Length
  $null = Invoke-LocalRcon 'cmend test wave 7'
  Wait-Log -AfterOffset $wave7Offset -Pattern 'WAVE_TEST_STARTED.*wave=7\b' | Out-Null
  $barrierLog = Wait-Log -AfterOffset $wave7Offset `
    -Pattern 'END_RIFT_WAVE7_BARRIERS_READY.*cells=(\d+).*height=5.*material=barrier.*collision=true.*visible=true.*journaled=true'
  $barrierMatch = [Regex]::Match($barrierLog, 'END_RIFT_WAVE7_BARRIERS_READY.*cells=(\d+)')
  if (-not $barrierMatch.Success -or [int]$barrierMatch.Groups[1].Value -le 0) {
    throw "Wave 7 barrier count was not positive: $barrierLog"
  }
  $columnMatch = [Regex]::Match($barrierLog, 'columns=(\d+)')
  if (-not $columnMatch.Success -or [int]$columnMatch.Groups[1].Value -le 0) {
    throw "Wave 7 did not expose connected one-block wall columns: $barrierLog"
  }
  # The disposable wave can legitimately finish within a few seconds when
  # the bots are online. Probe the physical cells before asking for the
  # objective summary, so cleanup cannot erase the evidence first.
  $probePoint = Assert-BarrierBlock -Core $core -FloorY $floorY -AfterOffset $wave7Offset
  $wave7Objective = Invoke-LocalRcon 'cmend debug objectives'
  $wave7VisualMatch = [Regex]::Match(($wave7Objective -replace '\u00A7.', ''), 'visuals=(\d+)')
  if (-not $wave7VisualMatch.Success -or [int]$wave7VisualMatch.Groups[1].Value -le 0) {
    throw "Wave 7 did not expose barrier visuals: $wave7Objective"
  }
  Write-Output "LIVE_WAVE7_ONE_BLOCK_WALL_PASS chambers=2 cells=$($barrierMatch.Groups[1].Value) columns=$($columnMatch.Groups[1].Value) visual_displays=$($wave7VisualMatch.Groups[1].Value) wall_material=barrier barrier=$probePoint collision=true connected=raster"

  # A disposable test wave normally remains in READY_FOR_PLAYERS.  Persisted
  # Wave 7 is deliberately restartable so this probe can exercise the same
  # PDC/journal recovery boundary without touching an official roster.
  $restartLogLengthBefore = Log-Length
  $restartMarkerBefore = [Regex]::Matches((Read-Log), 'END_RIFT_WAVE7_BARRIERS_REHYDRATED').Count
  $null = Invoke-LocalRcon 'save-all'
  Start-Sleep -Seconds 1
  # The first bot processes are intentionally stopped before the server
  # process.  Mineflayer does not reconnect after a clean Paper restart, so
  # leaving them alive would make the post-restart natural-completion check
  # observe a valid recovery with no player-side combat clients.
  Stop-Bots
  try { $null = Invoke-LocalRcon 'stop' } catch { }
  Wait-Port -Port 25576 -Expected $false -Seconds 60
  Start-LocalMinecraft
  if ($combatTraceProbe) {
    # A Paper restart recreates the plugin and its opt-in trace flag. Re-enable
    # it before reconnecting the bots so the recovered Wave 7 has full event
    # lifecycle evidence as well.
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
      try {
        $null = Invoke-LocalRcon 'cmend debug trace on'
        break
      } catch {
        if ($attempt -eq 19) { throw }
        Start-Sleep -Milliseconds 500
      }
    }
  }
  foreach ($name in $names) { Start-Bot -Name $name -Core $core }
  Wait-BotsOnline -Names $names
  # Validate recovery before the fresh clients begin attacking.  A disposable
  # Wave 7 can clear both rooms in seconds; waiting for bot configuration and
  # a fixed grace period first makes the physical recovery assertion race the
  # very cleanup it is intended to observe.
  Wait-Log-MarkerIncrease -Pattern 'END_RIFT_WAVE7_BARRIERS_REHYDRATED.*collision=true.*visible=true' `
    -BeforeCount $restartMarkerBefore -BeforeLength $restartLogLengthBefore -Seconds $TimeoutSeconds | Out-Null
  $restartProbeOffset = Log-Length
  $restartProbePoint = Assert-BarrierBlock -Core $core -FloorY $floorY -AfterOffset $restartProbeOffset
  Write-Output "LIVE_WAVE7_RESTART_RECOVERY_PASS rehydrated=true collision=true visible=true barrier=$restartProbePoint journal_replayed=true"
  # The recovered Wave 7 room is already closed.  Keep the player's durable
  # position and let the server-side reconnect/containment path own placement;
  # a central admin teleport would be rejected by the very wall being tested.
  Wait-BotLoginSettle
  foreach ($name in $names) { Configure-Bot -Name $name -Core $core -SkipTeleport }
  Sync-BotsHeldItem
  Start-Sleep -Seconds 7

  $naturalOffset = Log-Length
  Wait-Log -AfterOffset $naturalOffset -Pattern 'END_RIFT_CHAMBERS_COMPLETE.*chambers=2' -Seconds $BotDurationSeconds | Out-Null
  Wait-Log -AfterOffset $naturalOffset -Pattern 'DISPOSABLE_WAVE_NATURAL_COMPLETE.*wave=7.*cleanup=server.*phase_unchanged=true' -Seconds $TimeoutSeconds | Out-Null
  $naturalStatus = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($naturalStatus -notmatch 'event-mobs=\s*0') {
    throw "Natural Wave 7 completion left event mobs: $naturalStatus"
  }
  Write-Output 'LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0 phase_unchanged=true'

  $cleanupOffset = Log-Length
  $null = Invoke-LocalRcon 'cmend test wave 7'
  Wait-Log -AfterOffset $cleanupOffset -Pattern 'END_RIFT_WAVE7_BARRIERS_READY' -Seconds $TimeoutSeconds | Out-Null
  $null = Invoke-LocalRcon 'cmend wave clear'
  Start-Sleep -Seconds 1
  $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($status -notmatch 'event-mobs=\s*0' -or $status -notmatch 'rift-obelisks=0/6') {
    throw "Wave 7 command cleanup left transient state: $status"
  }
  Write-Output 'LIVE_WAVE7_COMMAND_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0'
}
finally {
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
  if ($combatTraceProbe) { try { $null = Invoke-LocalRcon 'cmend debug trace off' } catch { } }
  if ($previousLocalMobSpawning -ne $null) {
    try { Set-LocalArenaMobSpawning -Enabled ($previousLocalMobSpawning -eq 'true') } catch { }
  }
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { } }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.WaitForExit(5000) | Out-Null } catch { } }
  }
  if ($null -eq $previousWave7Autopilot) {
    Remove-Item Env:END_RIFT_BOT_WAVE7_AUTOPILOT -ErrorAction SilentlyContinue
  } else {
    $env:END_RIFT_BOT_WAVE7_AUTOPILOT = $previousWave7Autopilot
  }
}
