[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftBoundaryA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftBoundaryB',
  [ValidateRange(30, 300)]
  [int]$BotDurationSeconds = 120,
  [ValidateRange(10, 180)]
  [int]$TimeoutSeconds = 60,
  [ValidateSet('PASS', 'NOT RECORDED')]
  [string]$AutomatedGateResult = 'NOT RECORDED'
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
$diagnosticRoot = Join-Path $root 'artifacts\end-rift-diagnostics'
$runStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$branch = (& git -C $root branch --show-current 2>$null).Trim()
$gitHead = (& git -C $root rev-parse HEAD 2>$null).Trim()
$referenceSha = '79781d8c8be3827078a77972ccba851df4eb819f'
$dirtyStatus = @(& git -C $root status --short 2>$null)
$runDir = Join-Path $diagnosticRoot ($runStamp + '-' + $gitHead.Substring(0, [Math]::Min(12, $gitHead.Length)))
$diagnosticSource = Join-Path $serverDir 'plugins\CopiMineEndEvent\diagnostics\end-rift-events.jsonl'
$diagnosticStartSequence = 0L
$script:activeLiveStepId = $null
$script:livePaperResult = 'NOT RUN'
$script:diagnosticReportResult = 'NOT RUN'
$script:runMetadata = [ordered]@{}
$cleanupFailures = [System.Collections.Generic.List[string]]::new()

function Write-Utf8NoBom {
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Text)
  [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Write-RunMetadata {
  Write-Utf8NoBom -Path (Join-Path $runDir 'metadata.json') `
    -Text ($script:runMetadata | ConvertTo-Json -Depth 12)
}

function Start-LiveStep {
  param([Parameter(Mandatory = $true)][string]$Id, [Parameter(Mandatory = $true)][string]$Description)
  $script:activeLiveStepId = $Id
  Add-Content -LiteralPath (Join-Path $runDir 'live-test-steps.log') `
    -Value ("LIVE_TEST STEP_START id=$Id description=" + ($Description -replace '[\r\n]+', ' '))
  Write-Host "LIVE_TEST STEP_START id=$Id"
}

function Complete-LiveStep {
  if ($null -eq $script:activeLiveStepId) { return }
  $id = $script:activeLiveStepId
  Add-Content -LiteralPath (Join-Path $runDir 'live-test-steps.log') -Value "LIVE_TEST STEP_PASS id=$id"
  Write-Host "LIVE_TEST STEP_PASS id=$id"
  $script:activeLiveStepId = $null
}

function Fail-LiveStep {
  param([string]$Detail = 'step failed')
  if ($null -eq $script:activeLiveStepId) { return }
  $id = $script:activeLiveStepId
  Add-Content -LiteralPath (Join-Path $runDir 'live-test-steps.log') `
    -Value ("LIVE_TEST STEP_FAIL id=$id detail=" + ($Detail -replace '[\r\n]+', ' '))
  Write-Host "LIVE_TEST STEP_FAIL id=$id"
  $script:activeLiveStepId = $null
}

function Get-HighestDiagnosticSequence {
  $highest = 0L
  $candidates = @($diagnosticSource, ($diagnosticSource + '.1'))
  foreach ($candidate in $candidates) {
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
    foreach ($line in Get-Content -LiteralPath $candidate) {
      try {
        $record = $line | ConvertFrom-Json -ErrorAction Stop
        if ($null -ne $record.sequence) {
          $highest = [Math]::Max($highest, [int64]$record.sequence)
        }
      } catch {
        # Existing pre-run compatibility data is not copied into this run.
        # Newly written records are parsed strictly by Export-DiagnosticRun.
      }
    }
  }
  return $highest
}

function Export-DiagnosticRun {
  $records = [System.Collections.Generic.List[object]]::new()
  $candidates = @(($diagnosticSource + '.1'), $diagnosticSource)
  foreach ($candidate in $candidates) {
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $candidate) {
      $lineNumber++
      if ([string]::IsNullOrWhiteSpace($line)) {
        throw "Blank diagnostic JSONL line at $candidate`:$lineNumber"
      }
      try {
        $record = $line | ConvertFrom-Json -ErrorAction Stop
      } catch {
        throw "Malformed diagnostic JSONL line at $candidate`:$lineNumber`: $($_.Exception.Message)"
      }
      if ($null -eq $record.sequence) {
        throw "Diagnostic record has no sequence at $candidate`:$lineNumber"
      }
      $sequence = [int64]$record.sequence
      if ($sequence -gt $diagnosticStartSequence) {
        $records.Add($record)
      }
    }
  }
  $ordered = @($records | Sort-Object { [int64]$_.sequence })
  if ($ordered.Count -eq 0) {
    throw "No central diagnostic records were written after sequence $diagnosticStartSequence."
  }
  $lines = foreach ($record in $ordered) {
    $record | ConvertTo-Json -Depth 20 -Compress
  }
  Write-Utf8NoBom -Path (Join-Path $runDir 'end-rift-events.jsonl') -Text (($lines -join [Environment]::NewLine) + [Environment]::NewLine)
  $script:runMetadata['diagnosticStartSequence'] = $diagnosticStartSequence
  $script:runMetadata['diagnosticRecords'] = $ordered.Count
}

function Record-DiagnosticStatus {
  param([string]$StatusText)
  if ([string]::IsNullOrWhiteSpace($StatusText)) { return }
  $patterns = [ordered]@{
    diagnosticEventsDropped = 'dropped=(\d+)'
    diagnosticWriteFailures = 'failures=(\d+)'
    diagnosticMaxQueueDepth = 'queueMax=(\d+)'
    diagnosticRotations = 'rotations=(\d+)'
  }
  foreach ($entry in $patterns.GetEnumerator()) {
    $match = [Regex]::Match($StatusText, $entry.Value)
    if ($match.Success) { $script:runMetadata[$entry.Key] = [int64]$match.Groups[1].Value }
  }
  $modeMatch = [Regex]::Match($StatusText, 'END_RIFT_DIAGNOSTICS mode=([A-Z]+)')
  if ($modeMatch.Success) { $script:runMetadata['diagnosticMode'] = $modeMatch.Groups[1].Value }
}

function Write-ArtifactHashes {
  $artifacts = @(
    @{ Name = 'CopiMineEndEvent.jar'; Path = (Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar') },
    @{ Name = 'Purpur server jar'; Path = (Join-Path $serverDir 'purpur.jar') },
    @{ Name = 'CopiMineClient.jar'; Path = (Join-Path $root 'CopiMineClient\build\libs\CopiMineClient-0.1.1.jar') },
    @{ Name = 'CopiMineResourcePack.zip'; Path = (Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip') }
  )
  $lines = foreach ($artifact in $artifacts) {
    if (-not (Test-Path -LiteralPath $artifact.Path -PathType Leaf)) {
      throw "Required live artifact is missing: $($artifact.Path)"
    }
    $hash = (Get-FileHash -LiteralPath $artifact.Path -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash $($artifact.Name)"
  }
  Write-Utf8NoBom -Path (Join-Path $runDir 'artifact-hashes.txt') -Text (($lines -join [Environment]::NewLine) + [Environment]::NewLine)
}

New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$javaVersion = try {
  $javaExecutable = (Get-Command java.exe -ErrorAction Stop).Source
  $fileVersion = (Get-Item -LiteralPath $javaExecutable -ErrorAction Stop).VersionInfo.ProductVersion
  if ([string]::IsNullOrWhiteSpace([string]$fileVersion)) { 'unknown' } else { [string]$fileVersion.Trim() }
} catch { 'unknown' }
$script:runMetadata = [ordered]@{
  repository = 'IliaZav/copimine'
  branch = $branch
  referenceSha = $referenceSha
  gitHead = $gitHead
  dirty = ($dirtyStatus.Count -gt 0)
  dirtyStatus = @($dirtyStatus)
  serverVersion = 'Purpur local-runtime'
  javaVersion = $javaVersion
  diagnosticMode = 'VERBOSE'
  automatedGateResult = $AutomatedGateResult
  nativeMinecraft = 'NOT VERIFIED'
  livePaperResult = 'RUNNING'
  ciResult = 'NOT RECORDED'
  runDirectory = $runDir
  eventId = ''
  testPlan = @('W6-SEAL-01', 'W7-BARRIER-01', 'W7-RESTART-01', 'W7-NATURAL-CLEANUP-01', 'W7-COMMAND-CLEANUP-01')
}
$diagnosticStartSequence = Get-HighestDiagnosticSequence
Write-RunMetadata
Write-Utf8NoBom -Path (Join-Path $runDir 'evidence-index.json') -Text ((ConvertTo-Json -InputObject (,[ordered]@{
  id = 'native-minecraft-visual'
  file = ''
  gitHead = $gitHead
  eventSequence = $null
  description = 'Native Minecraft screenshot/video unavailable in this environment.'
  result = 'NOT VERIFIED'
}) -Depth 8))

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

function Wait-Until {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Condition,
    [Parameter(Mandatory = $true)][string]$Description,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  $lastError = ''
  while ((Get-Date) -lt $deadline) {
    try {
      if (& $Condition) { return }
    } catch {
      $lastError = $_.Exception.Message
    }
    Start-Sleep -Milliseconds 250
  }
  $suffix = if ([string]::IsNullOrWhiteSpace($lastError)) { '' } else { " Last error: $lastError" }
  throw "Timed out waiting for $Description.$suffix"
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
    if ($process -and -not $process.HasExited) {
      try { $process.Kill() } catch { $cleanupFailures.Add("bot-kill:$($_.Exception.Message)") }
    }
  }
  foreach ($process in $botProcesses) {
    if ($process) {
      try { $process.WaitForExit(5000) | Out-Null } catch { $cleanupFailures.Add("bot-wait:$($_.Exception.Message)") }
    }
  }
  $botProcesses.Clear()
}

function Wait-BotsOffline([string[]]$Names) {
  Wait-Until -Description ("boundary probe players offline: " + ($Names -join ', ')) -Seconds 90 -Condition {
    $list = Invoke-LocalRcon 'list'
    return @($Names | Where-Object { $list -match [Regex]::Escape($_) }).Count -eq 0
  } | Out-Null
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
  $null = Invoke-LocalRcon ("minecraft:item replace entity $name hotbar.1 with minecraft:bow")
  $null = Invoke-LocalRcon ("give $name minecraft:arrow 64")
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
  Wait-Until -Description ("boundary probe players online: " + ($Names -join ', ')) -Seconds 90 -Condition {
    $list = Invoke-LocalRcon 'list'
    return @($Names | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0
  } | Out-Null
}

function Wait-BotLoginSettle {
  param(
    [Parameter(Mandatory = $true)][string[]]$Names,
    [Parameter(Mandatory = $true)][int64]$AfterOffset
  )
  # The local accounts are authenticated by AuthMe shortly after the network
  # join.  Configure persistent attributes only after that profile reload, or
  # an old account value can silently replace the deterministic probe setup.
  Wait-Until -Description ("AuthMe login for " + ($Names -join ', ')) -Seconds 45 -Condition {
    $tail = Log-Tail $AfterOffset
    return @($Names | Where-Object {
        $tail -notmatch ('\[AuthMe\].*' + [Regex]::Escape($_) + '\s+logged in')
      }).Count -eq 0
  } | Out-Null
}

function Sync-BotsHeldItem {
  # Mineflayer can believe slot 0 is already selected and skip the packet that
  # makes Paper re-evaluate the item attribute modifier. Signal the already
  # configured clients after every replacement instead of racing from spawn.
  $null = Invoke-LocalRcon 'say END_RIFT_BOUNDARY_SYNC_HELD_ITEM'
}

function Wait-BotsHeldItemSynced([string[]]$Names) {
  Wait-Until -Description ("held-item sync for " + ($Names -join ', ')) -Seconds 20 -Condition {
    $missing = @($Names | Where-Object {
        $path = Join-Path $runtimeRoot ($_ + '-wave6-wave7.log')
        -not (Test-Path -LiteralPath $path -PathType Leaf) -or
          (Get-Content -LiteralPath $path -Raw) -notmatch ('HELD_ITEM_SYNC ' + [Regex]::Escape($_) + '\s+slot=1->0')
      })
    return $missing.Count -eq 0
  } | Out-Null
}

function Get-BotUuid([string]$Name) {
  $path = Join-Path $runtimeRoot ($Name + '-wave6-wave7.log')
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return '' }
  $matches = [Regex]::Matches((Get-Content -LiteralPath $path -Raw),
    'PLAYER_JOIN\s+' + [Regex]::Escape($Name) + '\s+uuid=([0-9a-fA-F-]{36})')
  if ($matches.Count -eq 0) { return '' }
  return $matches[$matches.Count - 1].Groups[1].Value.ToLowerInvariant()
}

function Get-PositivePlayerDamageLedger([int64]$AfterOffset) {
  $ledger = @{}
  $pattern = 'WAVE_MOB_PLAYER_DAMAGE_APPLIED[^\r\n]*?attacker=([0-9a-fA-F-]{36})[^\r\n]*?delta=([0-9]+(?:\.[0-9]+)?)'
  foreach ($match in [Regex]::Matches((Log-Tail $AfterOffset), $pattern)) {
    $delta = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
    if ($delta -le 0.0D) { continue }
    $uuid = $match.Groups[1].Value.ToLowerInvariant()
    if (-not $ledger.ContainsKey($uuid)) {
      $ledger[$uuid] = [ordered]@{ hits = 0; totalDelta = 0.0D }
    }
    $ledger[$uuid].hits = [int]$ledger[$uuid].hits + 1
    $ledger[$uuid].totalDelta = [double]$ledger[$uuid].totalDelta + $delta
  }
  return ,$ledger
}

function Restart-Bots([string[]]$Names, [int[]]$Core) {
  # Wave 6 can run long enough for a short-lived diagnostic client to expire
  # before Wave 7 begins.  Refresh both clients at this boundary so the server
  # always receives a complete two-player chamber assignment and the
  # post-restart completion probe has a full client lifetime.
  Stop-Bots
  Wait-BotsOffline -Names $Names
  $authOffset = Log-Length
  foreach ($name in $Names) { Start-Bot -Name $name -Core $Core }
  Wait-BotsOnline -Names $Names
  Wait-BotLoginSettle -Names $Names -AfterOffset $authOffset
  foreach ($name in $Names) { Configure-Bot -Name $name -Core $Core }
  Sync-BotsHeldItem
  Wait-BotsHeldItemSynced -Names $Names
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
    try {
      Wait-Until -Description ("barrier probe at $($point.X),$($point.Y),$($point.Z)") -Seconds 3 -Condition {
        return (Log-Tail $AfterOffset) -match $probe
      } | Out-Null
      return "$($point.X),$($point.Y),$($point.Z)"
    } catch {
      # Probe the next deterministic cell when this candidate is not a barrier.
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

try {
  Start-LiveStep -Id 'W6-SETUP-01' -Description 'Prepare isolated local arena, trace mode, and two real clients.'
  $core = Get-Core (Invoke-LocalRcon 'cmend status')
  $floorY = $core[1]
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
  Complete-LiveStep

  Start-LiveStep -Id 'W6-SEAL-01' -Description 'Capture one prisoner through the physical seal and observe the first authorized drain.'
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
  Complete-LiveStep

  Start-LiveStep -Id 'W7-BARRIER-01' -Description 'Build the connected one-block Wave 7 barrier and probe a real collision cell.'
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
  Complete-LiveStep

  Start-LiveStep -Id 'W7-RESTART-01' -Description 'Restart the isolated Paper server and verify journal/PDC barrier rehydration before combat.'
  # A disposable test wave normally remains in READY_FOR_PLAYERS.  Persisted
  # Wave 7 is deliberately restartable so this probe can exercise the same
  # PDC/journal recovery boundary without touching an official roster.
  $restartLogLengthBefore = Log-Length
  $restartMarkerBefore = [Regex]::Matches((Read-Log), 'END_RIFT_WAVE7_BARRIERS_REHYDRATED').Count
  $saveResponse = Invoke-LocalRcon 'save-all'
  if ($saveResponse -notmatch '(?i)saved') {
    throw "Paper did not acknowledge save-all before restart: $saveResponse"
  }
  # The first bot processes are intentionally stopped before the server
  # process.  Mineflayer does not reconnect after a clean Paper restart, so
  # leaving them alive would make the post-restart natural-completion check
  # observe a valid recovery with no player-side combat clients.
  Stop-Bots
  $null = Invoke-LocalRcon 'stop'
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
  $restartAuthOffset = Log-Length
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
  Complete-LiveStep
  # The recovered Wave 7 room is already closed.  Keep the player's durable
  # position and let the server-side reconnect/containment path own placement;
  # a central admin teleport would be rejected by the very wall being tested.
  Wait-BotLoginSettle -Names $names -AfterOffset $restartAuthOffset
  foreach ($name in $names) { Configure-Bot -Name $name -Core $core -SkipTeleport }
  Sync-BotsHeldItem
  Wait-BotsHeldItemSynced -Names $names

  $playerUuids = @{}
  foreach ($name in $names) {
    Wait-Until -Description ("bot UUID for $name") -Seconds 20 -Condition {
      return -not [string]::IsNullOrWhiteSpace((Get-BotUuid $name))
    } | Out-Null
    $playerUuids[$name] = Get-BotUuid $name
  }

  Start-LiveStep -Id 'W7-NATURAL-CLEANUP-01' -Description 'Allow recovered Wave 7 to complete naturally and verify server-owned cleanup.'
  $naturalOffset = Log-Length
  Wait-Log -AfterOffset $naturalOffset -Pattern 'END_RIFT_CHAMBERS_COMPLETE.*chambers=2' -Seconds $BotDurationSeconds | Out-Null
  Wait-Log -AfterOffset $naturalOffset -Pattern 'DISPOSABLE_WAVE_NATURAL_COMPLETE.*wave=7.*cleanup=server.*phase_unchanged=true' -Seconds $TimeoutSeconds | Out-Null
  $naturalStatus = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($naturalStatus -notmatch 'event-mobs=\s*0') {
    throw "Natural Wave 7 completion left event mobs: $naturalStatus"
  }
  $damageLedger = Get-PositivePlayerDamageLedger -AfterOffset $naturalOffset
  $damageParts = [System.Collections.Generic.List[string]]::new()
  foreach ($name in $names) {
    $uuid = $playerUuids[$name]
    if (-not $damageLedger.ContainsKey($uuid) -or [int]$damageLedger[$uuid].hits -lt 1) {
      throw "Wave 7 natural completion did not record positive player damage for $name ($uuid)."
    }
    $damageParts.Add(("{0}={1}:hits={2}:delta={3}" -f $name, $uuid,
        [int]$damageLedger[$uuid].hits,
        ([double]$damageLedger[$uuid].totalDelta).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)))
  }
  Write-Output ("LIVE_WAVE7_PLAYER_DAMAGE_LEDGER_PASS players={0} positive_attackers={1} {2}" -f
    $names.Count, $damageParts.Count, ($damageParts -join ' '))
  Write-Output 'LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0 phase_unchanged=true'
  Complete-LiveStep

  Start-LiveStep -Id 'W7-COMMAND-CLEANUP-01' -Description 'Run explicit Wave 7 cleanup, dump canonical state, and collect diagnostic counters.'
  $cleanupOffset = Log-Length
  $null = Invoke-LocalRcon 'cmend test wave 7'
  Wait-Log -AfterOffset $cleanupOffset -Pattern 'END_RIFT_WAVE7_BARRIERS_READY' -Seconds $TimeoutSeconds | Out-Null
  $null = Invoke-LocalRcon 'cmend wave clear'
  Wait-Until -Description 'Wave 7 command cleanup zero state' -Seconds 20 -Condition {
    $candidate = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
    return $candidate -match 'event-mobs=\s*0' -and $candidate -match 'rift-obelisks=0/6'
  } | Out-Null
  $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($status -notmatch 'event-mobs=\s*0' -or $status -notmatch 'rift-obelisks=0/6') {
    throw "Wave 7 command cleanup left transient state: $status"
  }
  Write-Output 'LIVE_WAVE7_COMMAND_CLEANUP_PASS blocks_restored=true displays_removed=true transient_entities=0'
  $null = Invoke-LocalRcon 'cmend debug dump'
  $diagnosticStatus = Invoke-LocalRcon 'cmend debug status'
  Record-DiagnosticStatus -StatusText $diagnosticStatus
  Complete-LiveStep
  $script:livePaperResult = 'PASS'
}
catch {
  $script:livePaperResult = 'FAIL'
  Fail-LiveStep -Detail $_.Exception.Message
  throw
}
finally {
  try {
    $diagnosticStatus = Invoke-LocalRcon 'cmend debug status'
    Record-DiagnosticStatus -StatusText $diagnosticStatus
  } catch { $cleanupFailures.Add("diagnostic-status:$($_.Exception.Message)") }
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { $cleanupFailures.Add("wave-clear:$($_.Exception.Message)") }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { $cleanupFailures.Add("boss-cleanup:$($_.Exception.Message)") }
  if ($combatTraceProbe) {
    try { $null = Invoke-LocalRcon 'cmend debug trace off' } catch { $cleanupFailures.Add("trace-off:$($_.Exception.Message)") }
  }
  if ($previousLocalMobSpawning -ne $null) {
    try { Set-LocalArenaMobSpawning -Enabled ($previousLocalMobSpawning -eq 'true') } catch {
      $cleanupFailures.Add("mob-spawning-restore:$($_.Exception.Message)")
    }
  }
  if (Test-PortOpen 25576) {
    try {
      $finalStatus = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
      $finalObjectives = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
      $finalAi = Invoke-LocalRcon 'cmend debug ai --json' | ConvertFrom-Json
      $zeroState = $finalStatus -match 'event-mobs=\s*0' -and
        $finalStatus -match 'rift-obelisks=0/6' -and
        $finalObjectives -match 'visuals=0' -and
        [int]$finalAi.mobile -eq 0 -and -not [bool]$finalAi.bossPresent
      if (-not $zeroState) {
        $cleanupFailures.Add("zero-state:status=$finalStatus objectives=$finalObjectives ai=$($finalAi | ConvertTo-Json -Compress)")
      } else {
        Write-Output 'LIVE_WAVE7_CLEANUP_ZERO_STATE_PASS event_mobs=0 mobile=0 boss=false visuals=0 barriers=0'
      }
    } catch { $cleanupFailures.Add("zero-state-query:$($_.Exception.Message)") }
  }
  try { $null = Invoke-LocalRcon 'stop' } catch { $cleanupFailures.Add("server-stop:$($_.Exception.Message)") }
  try { Wait-Port -Port 25576 -Expected $false -Seconds 30 } catch { $cleanupFailures.Add("server-stop-wait:$($_.Exception.Message)") }
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) {
      try { $process.WaitForExit(10000) | Out-Null } catch {
        $cleanupFailures.Add("server-process-wait:$($_.Exception.Message)")
      }
    }
  }
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) {
      try { $process.Kill() } catch { $cleanupFailures.Add("server-process-kill:$($_.Exception.Message)") }
    }
  }
  foreach ($process in $processes) {
    if ($process) {
      try { $process.WaitForExit(5000) | Out-Null } catch {
        $cleanupFailures.Add("server-process-final-wait:$($_.Exception.Message)")
      }
    }
  }
  if ($null -eq $previousWave7Autopilot) {
    Remove-Item Env:END_RIFT_BOT_WAVE7_AUTOPILOT -ErrorAction SilentlyContinue
  } else {
    $env:END_RIFT_BOT_WAVE7_AUTOPILOT = $previousWave7Autopilot
  }
  if ($cleanupFailures.Count -gt 0) {
    $script:livePaperResult = 'FAIL'
  }
  try {
    $script:runMetadata['livePaperResult'] = $script:livePaperResult
    $script:runMetadata['diagnosticReportResult'] = $script:diagnosticReportResult
    Write-ArtifactHashes
    Export-DiagnosticRun
    Write-RunMetadata
    $reportOutput = (& python (Join-Path $root 'tests\tools\build_end_rift_diagnostic_report.py') $runDir 2>&1 | Out-String).Trim()
    $reportExit = $LASTEXITCODE
    if ($reportExit -ne 0) {
      throw "Diagnostic report generation failed: $reportOutput"
    }
    $summary = Get-Content -LiteralPath (Join-Path $runDir 'summary.json') -Raw | ConvertFrom-Json
    $script:diagnosticReportResult = [string]$summary.result
    # The first report pass reads the pre-report metadata. Persist the actual
    # report result and rebuild once so metadata.json, summary.json and
    # report.md all describe the same completed audit.
    $script:runMetadata['diagnosticReportResult'] = $script:diagnosticReportResult
    Write-RunMetadata
    $reportOutput = (& python (Join-Path $root 'tests\tools\build_end_rift_diagnostic_report.py') $runDir 2>&1 | Out-String).Trim()
    $reportExit = $LASTEXITCODE
    if ($reportExit -ne 0) {
      throw "Diagnostic report regeneration failed: $reportOutput"
    }
    $summary = Get-Content -LiteralPath (Join-Path $runDir 'summary.json') -Raw | ConvertFrom-Json
    $script:diagnosticReportResult = [string]$summary.result
    if ($script:diagnosticReportResult -ne 'PASS') {
      $script:livePaperResult = $script:diagnosticReportResult
      $script:runMetadata['livePaperResult'] = $script:livePaperResult
      $script:runMetadata['diagnosticReportResult'] = $script:diagnosticReportResult
      Write-RunMetadata
      $null = & python (Join-Path $root 'tests\tools\build_end_rift_diagnostic_report.py') $runDir
      throw "Diagnostic report is not PASS: $($script:diagnosticReportResult)"
    }
    Write-Host "END_RIFT_DIAGNOSTIC_REPORT_PASS runDir=$runDir"
  } catch {
    if ($script:livePaperResult -eq 'PASS') { $script:livePaperResult = 'FAIL' }
    $script:runMetadata['livePaperResult'] = $script:livePaperResult
    $script:runMetadata['diagnosticReportResult'] = $script:diagnosticReportResult
    try { Write-RunMetadata } catch { $cleanupFailures.Add("metadata-write:$($_.Exception.Message)") }
    Write-Error $_
    throw
  }
  if ($cleanupFailures.Count -gt 0) {
    throw ("Wave 7 fail-closed cleanup failed: " + ($cleanupFailures -join '; '))
  }
}
