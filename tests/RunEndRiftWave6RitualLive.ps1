[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftWave6A',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftWave6B',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$ThirdBotName = 'EndRiftWave6C',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FourthBotName = 'EndRiftWave6D',
  [ValidateRange(90, 600)]
  [int]$BotDurationSeconds = 240,
  [ValidateRange(30, 300)]
  [int]$TimeoutSeconds = 150
)

# Local/staging-only Wave 6 acceptance probe.  The probe deliberately drives
# the real Paper plugin, real Mineflayer clients, RCON commands, and the
# server log.  It never exposes an ordinary-player command or points at a
# production port.  The source/pure policy gates remain the fast checks; this
# file is the fresh runtime evidence boundary for the encounter.
# Isolated ports are server-port=25566 and rcon.port=25576.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$controlDirectory = Join-Path $runtimeRoot ('wave6-ritual-control-' + (Get-Date -Format 'yyyyMMddHHmmssfff'))
$evidencePath = Join-Path $runtimeRoot ('wave6-ritual-live-' + (Get-Date -Format 'yyyyMMddHHmmssfff') + '.log')
$processes = [System.Collections.Generic.List[object]]::new()
$botProcesses = [System.Collections.Generic.List[object]]::new()
$serverStartedByProbe = $false
$serverProcess = $null
$previousLocalMobSpawning = $null
$previousNaturalRegeneration = $null
$previousTrace = $null
$previousBotPassword = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_PASSWORD', 'Process')
$previousSkipRegister = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_SKIP_REGISTER', 'Process')
$names = @($FirstBotName, $SecondBotName, $ThirdBotName, $FourthBotName)
$cleanupFailures = [System.Collections.Generic.List[string]]::new()

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

function Test-PortOpen {
  param([Parameter(Mandatory = $true)][int]$Port)
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
    [int]$Seconds = 90
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if ((Test-PortOpen -Port $Port) -eq $Expected) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Port $Port did not reach expected state open=$Expected."
}

function Read-LocalEnvironment {
  $envPath = Join-Path $runtimeRoot 'end-rift.env'
  if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw "Local environment file is missing: $envPath"
  }
  foreach ($line in Get-Content -LiteralPath $envPath) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      Set-Item -Path ('Env:' + $matches[1]) -Value $matches[2].Trim().Trim('"').Trim("'")
    }
  }
  $env:COPIMINE_ENV_FILE = $envPath
}

function Start-LocalMinecraft {
  $pluginSource = Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar'
  $pluginTarget = Join-Path $serverDir 'plugins\CopiMineEndEvent.jar'
  if (-not (Test-Path -LiteralPath $pluginSource -PathType Leaf)) {
    throw "Current End Rift plugin build is missing: $pluginSource"
  }
  if ((Test-PortOpen -Port 25576) -and (Test-Path -LiteralPath $pluginTarget -PathType Leaf)) {
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginSource).Hash
    $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginTarget).Hash
    if ($sourceHash -ne $targetHash) {
      # The local Paper process has an older classloader.  Restart only this
      # isolated 25566/25576 server so the live proof cannot run old code.
      $null = Invoke-LocalRcon 'stop'
      Wait-Port -Port 25576 -Expected $false -Seconds 90
    }
  }
  if (Test-PortOpen -Port 25576) { return }
  $startScript = Join-Path $root 'tests\StartEndRiftLocal.ps1'
  if (-not (Test-Path -LiteralPath $startScript -PathType Leaf)) {
    throw "Local startup script is missing: $startScript"
  }
  & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $startScript -ReadyTimeoutSeconds 360
  if ($LASTEXITCODE -ne 0) { throw 'Local Paper startup failed.' }
  $script:serverStartedByProbe = $true
  Wait-Port -Port 25576 -Expected $true -Seconds 90
  $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginSource).Hash
  $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginTarget).Hash
  if ($sourceHash -ne $targetHash) {
    throw "Local Paper plugin SHA-256 mismatch after startup: source=$sourceHash target=$targetHash"
  }
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON failed: $CommandText $result"
  }
  return $result.Trim()
}

function Read-PaperLog { return [string](Get-Content -LiteralPath $paperLog -Raw) }
function Get-LogLength { return [int64](Read-PaperLog).Length }
function Get-LogTail {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $text = Read-PaperLog
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
    $tail = Get-LogTail -Offset $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'. See $paperLog"
}

function Wait-Until {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Condition,
    [Parameter(Mandatory = $true)][string]$Description,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $result = & $Condition
      if ($result) { return $result }
    } catch {
      # Entity and RCON state can be between ticks. Retry the condition and
      # report a hard failure only after the bounded deadline.
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for condition: $Description"
}

function Get-AiJson {
  $raw = (Invoke-LocalRcon 'cmend debug ai --json') -replace '\u00A7.', ''
  try {
    $snapshot = $raw | ConvertFrom-Json -ErrorAction Stop
  } catch {
    throw "Wave 6 structured AI diagnostics were not valid JSON: $raw"
  }
  if ($snapshot.type -ne 'END_RIFT_AI_DIAGNOSTICS') {
    throw "Wave 6 structured AI diagnostics had an unexpected type: $raw"
  }
  return $snapshot
}

function Assert-RitualCasterDiagnostics {
  param(
    [Parameter(Mandatory = $true)][string]$ExpectedState,
    [Parameter(Mandatory = $true)][int]$ExpectedCasters,
    [Parameter(Mandatory = $true)][int]$ExpectedGuards
  )
  $snapshot = Get-AiJson
  $casters = if ($null -eq $snapshot.casters) { @() } else { @($snapshot.casters) }
  if ([int]$snapshot.ritualCasters -ne $ExpectedCasters -or $casters.Count -ne $ExpectedCasters `
      -or [int]$snapshot.ritualGuards -ne $ExpectedGuards `
      -or -not [bool]$snapshot.ritualGuardOwnershipValid) {
    throw "Ritual caster ownership diagnostics failed: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
  }
  if (@($casters | Where-Object { $_.state -ne $ExpectedState }).Count -gt 0) {
    throw "Expected all ritual casters to be ${ExpectedState}: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
  }
  $expectedPassive = @($casters | Where-Object { -not [bool]$_.nativeAiEnabled }).Count
  $expectedEnabled = [int]$snapshot.mobile - $expectedPassive
  if ([int]$snapshot.aiEnabled -ne $expectedEnabled) {
    throw "Mixed AI ownership count was wrong: $($snapshot | ConvertTo-Json -Depth 12 -Compress)"
  }
  return $snapshot
}

function Wait-Log-MarkerIncrease {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int]$BeforeCount,
    [Parameter(Mandatory = $true)][int64]$BeforeLength,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Path -LiteralPath $paperLog -PathType Leaf) {
      $current = Read-PaperLog
      $count = [Regex]::Matches($current, $Pattern).Count
      # Paper may rotate latest.log at a clean process boundary.  A marker in
      # the new file is valid evidence even if its count is not greater than
      # the previous file's count.
      $rotated = $BeforeLength -gt 0L -and $current.Length -lt $BeforeLength
      if ($count -gt $BeforeCount -or ($rotated -and $count -gt 0)) {
        if ($rotated) { return $current }
        if ($BeforeLength -ge $current.Length) { return '' }
        return $current.Substring([int]$BeforeLength)
      }
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for a new log marker matching '$Pattern'. See $paperLog"
}

function Get-AppliedRitualDrainCount {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  return [Regex]::Matches((Get-LogTail -Offset $Offset),
    'WAVE6_RITUAL_PRISONER_DRAIN[^\r\n]*applied=true').Count
}

function Get-Core([string]$Status) {
  $match = [Regex]::Match(($Status -replace '\u00A7.', ''), 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core is missing from status: $Status" }
  return [int[]]@([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Get-OfflinePlayerUuid {
  param([Parameter(Mandatory = $true)][string]$Name)
  $md5 = [Security.Cryptography.MD5]::Create()
  try {
    $bytes = [Text.Encoding]::UTF8.GetBytes('OfflinePlayer:' + $Name)
    $hash = $md5.ComputeHash($bytes)
  } finally {
    $md5.Dispose()
  }
  # Paper's offline profile uses UUID.nameUUIDFromBytes semantics: the name
  # MD5 is normalized to RFC version 3 / variant 2 before it is exposed by
  # the server.  Compare this exact form with the capture log.
  $hash[6] = [byte](($hash[6] -band 0x0f) -bor 0x30)
  $hash[8] = [byte](($hash[8] -band 0x3f) -bor 0x80)
  return (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Get-UuidRegexPattern {
  param([Parameter(Mandatory = $true)][string]$Uuid)
  $clean = ($Uuid -replace '[^0-9a-fA-F]', '')
  if ($clean.Length -ne 32) { throw "Invalid UUID for log matching: $Uuid" }
  return ([Regex]::Escape($clean.Substring(0, 8)) + '-?' +
    [Regex]::Escape($clean.Substring(8, 4)) + '-?' +
    [Regex]::Escape($clean.Substring(12, 4)) + '-?' +
    [Regex]::Escape($clean.Substring(16, 4)) + '-?' +
    [Regex]::Escape($clean.Substring(20, 12)))
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

function Get-PlayerHealth([string]$Name) {
  $output = Invoke-LocalRcon ("data get entity $Name Health")
  $matches = [Regex]::Matches($output, '(?<![A-Za-z])(-?\d+(?:\.\d+)?)(?:f)?')
  if ($matches.Count -eq 0) { throw "Player health is not readable for ${Name}: $output" }
  return [double]$matches[$matches.Count - 1].Groups[1].Value
}

function Get-PlayerEffects([string]$Name) {
  # `data get entity ... active_effects` may abbreviate a long NBT list with
  # an ellipsis.  Read each bounded list entry separately so the assertion
  # sees the actual effect ids instead of a pretty-printer summary.
  $effects = [System.Collections.Generic.List[string]]::new()
  for ($index = 0; $index -lt 16; $index++) {
    $entry = Invoke-LocalRcon ("data get entity $Name active_effects[$index]")
    if ($entry -match '(?i)no value|cannot find|nothing') { break }
    $effects.Add($entry.ToLowerInvariant())
  }
  return ($effects -join ' ')
}

function Assert-HealthEqual {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][double]$Expected,
    [Parameter(Mandatory = $true)][string]$Reason
  )
  $actual = Get-PlayerHealth -Name $Name
  if ([Math]::Abs($actual - $Expected) -gt 0.01D) {
    throw "${Reason}: expected health $Expected, actual $actual"
  }
  return $actual
}

function Set-BotMode {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][ValidateSet('PASSIVE', 'ACTIVE')][string]$Mode
  )
  Set-Content -LiteralPath (Join-Path $controlDirectory ($Name + '.mode')) `
    -Value $Mode -NoNewline -Encoding ASCII
}

function Start-Bot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][int[]]$Core)
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $output = Join-Path $runtimeRoot ($Name + '-wave6-ritual.log')
  $error = Join-Path $runtimeRoot ($Name + '-wave6-ritual.err.log')
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 6.0D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 250 "' + $controlDirectory + '"'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $output -RedirectStandardError $error -WindowStyle Hidden -PassThru
  $processes.Add($process)
  $botProcesses.Add($process)
}

function Stop-Bots {
  foreach ($process in $botProcesses) {
    if ($process -and -not $process.HasExited) {
      try { $process.Kill() } catch { $cleanupFailures.Add('bot kill: ' + $_.Exception.Message) }
    }
  }
  foreach ($process in $botProcesses) {
    if ($process) {
      try { $process.WaitForExit(5000) | Out-Null } catch { $cleanupFailures.Add('bot wait: ' + $_.Exception.Message) }
    }
  }
  $botProcesses.Clear()
}

function Wait-BotsOnline {
  Wait-Until -Description ("Wave 6 probe players online: " + ($names -join ', ')) -Seconds 90 -Condition {
    $list = Invoke-LocalRcon 'list'
    return @($names | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0
  } | Out-Null
}

function Wait-BotsOffline {
  Wait-Until -Description ("Wave 6 probe players offline: " + ($names -join ', ')) -Seconds 90 -Condition {
    $list = Invoke-LocalRcon 'list'
    return @($names | Where-Object { $list -match [Regex]::Escape($_) }).Count -eq 0
  } | Out-Null
}

function Wait-BotOnline {
  param([Parameter(Mandatory = $true)][string]$Name)
  Wait-Until -Description "Wave 6 probe player online: $Name" -Seconds 90 -Condition {
    return (Invoke-LocalRcon 'list') -match [Regex]::Escape($Name)
  } | Out-Null
}

function Configure-Bots([int[]]$Core) {
  foreach ($name in $names) {
    $null = Invoke-LocalRcon ("gamemode survival $name")
    $null = Invoke-LocalRcon ("clear $name")
    # Keep every client at a safe disposable health while it waits outside
    # the seal.  The prisoner is reduced to five HP only after capture, so
    # guards cannot kill the future prisoner before the capture assertion.
    $null = Invoke-LocalRcon ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon ("attribute $name minecraft:generic.knockback_resistance base set 1")
    $null = Invoke-LocalRcon ("effect clear $name")
    $null = Invoke-LocalRcon ("effect give $name minecraft:resistance 1000 4 true")
    if ($name -eq $SecondBotName) {
      # Keep the future prisoner alive until the seal capture.  This is a
      # disposable client-side buffer, cleared immediately after capture;
      # prisoner damage immunity is still asserted against real damage.
      $null = Invoke-LocalRcon ("effect give $name minecraft:absorption 1000 255 true")
    }
    $null = Invoke-LocalRcon ("give $name minecraft:netherite_sword 1")
    $null = Invoke-LocalRcon ("enchant $name minecraft:sharpness 5")
    $null = Invoke-LocalRcon ("effect give $name minecraft:strength 1000 20 true")
    $null = Invoke-LocalRcon ("data merge entity $name {Health:1000.0f}")
    $null = Invoke-LocalRcon ("effect give $name minecraft:instant_health 1 10 true")
    Set-BotMode -Name $name -Mode PASSIVE
    Teleport-Player -Name $name -X ($Core[0] + 6.0D) -Y $Core[1] -Z ($Core[2] + 0.5D)
  }
}

function Restart-LocalMinecraftForWave6([int[]]$Core) {
  $restartLogLengthBefore = Get-LogLength
  $restartMarkerBefore = [Regex]::Matches((Read-PaperLog), 'WAVE6_RITUAL_REHYDRATED').Count
  $appliedDrainPattern = 'WAVE6_RITUAL_PRISONER_DRAIN[^\r\n]*applied=true[^\r\n]*drain_at=(\d+)'
  $restartAppliedDrainBefore = [Regex]::Matches((Read-PaperLog), $appliedDrainPattern).Count
  # Stop the clients before saving.  A short save delay while the probes are
  # still ACTIVE can legitimately kill one ritual guard and turn a restart
  # test into an entity-loss test unrelated to persistence.
  Stop-Bots
  Wait-BotsOffline
  $null = Invoke-LocalRcon 'save-all'
  try { $null = Invoke-LocalRcon 'stop' } catch { $cleanupFailures.Add('restart stop: ' + $_.Exception.Message) }
  Wait-Port -Port 25576 -Expected $false -Seconds 90
  Start-LocalMinecraft
  for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try {
      $null = Invoke-LocalRcon 'cmend debug trace on'
      break
    } catch {
      if ($attempt -eq 19) { throw }
      Start-Sleep -Milliseconds 500
    }
  }
  $restartLog = Wait-Log-MarkerIncrease -Pattern 'WAVE6_RITUAL_REHYDRATED[^\r\n]*casters=4[^\r\n]*guards=12' -BeforeCount $restartMarkerBefore -BeforeLength $restartLogLengthBefore -Seconds $TimeoutSeconds

  # Rejoin the persisted prisoner first.  If free participants joined first,
  # the normal replacement safety rule could legitimately transfer the
  # prisoner role while the original account was still offline; that would
  # turn a restart check into a different encounter.
  Set-BotMode -Name $SecondBotName -Mode PASSIVE
  Start-Bot -Name $SecondBotName -Core $Core
  Wait-BotOnline -Name $SecondBotName
  foreach ($name in @($FirstBotName, $ThirdBotName, $FourthBotName)) {
    Set-BotMode -Name $name -Mode PASSIVE
    Start-Bot -Name $name -Core $Core
  }
  Wait-BotsOnline
  $restartStatus = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  if ($restartStatus -notmatch 'wave=\s*6' -or $restartStatus -notmatch 'event-mobs=\s*16') {
    throw "Wave 6 restart lost active ritual state: $restartStatus"
  }
  $restartObjective = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
  $restartVisualMatch = [Regex]::Match($restartObjective, 'visuals=(\d+)')
  if (-not $restartVisualMatch.Success -or [int]$restartVisualMatch.Groups[1].Value -lt 1) {
    throw "Wave 6 restart lost the Ritual Sphere visual: $restartObjective"
  }
  $currentLog = Read-PaperLog
  $captureCount = [Regex]::Matches($currentLog, 'WAVE6_RITUAL_PRISONER_CAPTURED').Count
  $rotated = $restartLogLengthBefore -gt 0L -and $currentLog.Length -lt $restartLogLengthBefore
  if (-not $rotated -and $captureCount -ne 1) {
    throw "Wave 6 restart duplicated or lost capture marker: count=$captureCount"
  }
  if ($rotated) {
    # The old capture record lives in the rotated file.  Keep the continuation
    # offset at zero so the post-restart drain assertion reads the new file.
    $script:captureOffset = 0L
  } else {
    $script:captureOffset = $script:waveOffset
  }
  $replayedOverdueDrain = $false
  $nowAfterRestartMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if ($script:firstDrainAtMillis -le $nowAfterRestartMillis) {
    $restartDrainLog = Wait-Log-MarkerIncrease -Pattern $appliedDrainPattern `
      -BeforeCount $restartAppliedDrainBefore -BeforeLength $restartLogLengthBefore -Seconds 30
    $restartDrainMatches = [Regex]::Matches($restartDrainLog, $appliedDrainPattern)
    if ($restartDrainMatches.Count -lt 1) {
      throw 'Wave 6 restart did not replay the overdue captured-prisoner drain.'
    }
    $restartDrainMatch = $restartDrainMatches[$restartDrainMatches.Count - 1]
    $script:firstDrainAtMillis = [long]$restartDrainMatch.Groups[1].Value + 20000L
    $replayedOverdueDrain = $true
  }
  Write-Evidence "LIVE_WAVE6_RESTART_RECOVERY_PASS rehydrated=true phase=READY_FOR_PLAYERS wave=6 casters=4 guards=12 visual_displays=$($restartVisualMatch.Groups[1].Value) prisoner_preserved=true capture_replayed=false overdue_drain_replayed=$replayedOverdueDrain log_rotated=$rotated"
}

function Clear-LocalArenaAmbientMobs([int[]]$Core) {
  # Remove only ordinary hostile entities in the disposable local arena
  # before the four probe clients join.  Event-owned mobs are recreated by
  # the Wave 6 command immediately afterward; no production world is touched.
  $position = "$($Core[0] + 0.5D) $($Core[1]) $($Core[2] + 0.5D)"
  foreach ($mobType in @('minecraft:spider', 'minecraft:enderman', 'minecraft:skeleton')) {
    $null = Invoke-LocalRcon ("execute positioned $position run kill @e[type=$mobType,distance=..32]")
  }
}

function Assert-ExternalDamageIgnored([double]$ExpectedHealth) {
  $damageCases = @(
    @{ Label = 'melee'; Type = 'minecraft:player_attack' },
    @{ Label = 'projectile'; Type = 'minecraft:arrow' },
    @{ Label = 'generic'; Type = 'minecraft:generic' },
    @{ Label = 'fall/environment'; Type = 'minecraft:fall' }
  )
  foreach ($damageCase in $damageCases) {
    $null = Invoke-LocalRcon ("damage $SecondBotName 1 $($damageCase.Type)")
    Start-Sleep -Milliseconds 150
    Assert-HealthEqual -Name $SecondBotName -Expected $ExpectedHealth `
      -Reason ("captured prisoner external damage must be ignored: " + $damageCase.Label) | Out-Null
  }
  $tail = Get-LogTail -Offset $script:captureOffset
  if ($tail -notmatch 'WAVE6_RITUAL_PRISONER_DAMAGE_GUARDED') {
    throw 'No live external-damage immunity marker was emitted for the captured prisoner.'
  }
  Write-Evidence "LIVE_WAVE6_EXTERNAL_DAMAGE_IMMUNITY_PASS health=$ExpectedHealth cases=melee,projectile,generic,fall"
}

function Parse-LoggedCenter([string]$Text) {
  $match = [Regex]::Match($Text, 'center=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Zone center is missing from live marker: $Text" }
  $x = [double]$match.Groups[1].Value
  $y = [double]$match.Groups[2].Value
  $z = [double]$match.Groups[3].Value
  return [double[]]@(($x + 0.5D), $y, ($z + 0.5D))
}

if (@($names | Select-Object -Unique).Count -ne $names.Count) {
  throw 'Wave 6 probe bot names must be unique.'
}
$configText = Get-Content -LiteralPath $configPath -Raw
if ($configText -notmatch '(?m)^environment:\s*(local|staging)\s*$') {
  throw 'Refused: Wave 6 live probe accepts only environment: local or environment: staging.'
}
if ($configText -match '(?mi)^environment:\s*production\s*$') {
  throw 'Refused: Wave 6 live probe cannot run in production.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused Git branch '$branch'."
}
$properties = Get-Content -LiteralPath $propertiesPath -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Refused: Wave 6 probe requires isolated local ports server=25566 rcon=25576.'
}
if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
  throw "Paper log is missing: $paperLog"
}
New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null

$firstUuid = Get-OfflinePlayerUuid -Name $FirstBotName
$secondUuid = Get-OfflinePlayerUuid -Name $SecondBotName
$thirdUuid = Get-OfflinePlayerUuid -Name $ThirdBotName
$fourthUuid = Get-OfflinePlayerUuid -Name $FourthBotName
$firstUuidPattern = Get-UuidRegexPattern -Uuid $firstUuid
$secondUuidPattern = Get-UuidRegexPattern -Uuid $secondUuid
$thirdUuidPattern = Get-UuidRegexPattern -Uuid $thirdUuid
$fourthUuidPattern = Get-UuidRegexPattern -Uuid $fourthUuid
$freeUuidPattern = '(?:' + $firstUuidPattern + '|' + $thirdUuidPattern + '|' + $fourthUuidPattern + ')'
$orderedUuids = @(
  [pscustomobject]@{ Name = $FirstBotName; Uuid = $firstUuid },
  [pscustomobject]@{ Name = $SecondBotName; Uuid = $secondUuid }
) | Sort-Object Uuid
if ($orderedUuids[0].Name -ne $FirstBotName) {
  throw "Capture-order precondition failed: $FirstBotName must have the lexicographically smaller UUID."
}

try {
  Start-LocalMinecraft
  $status = Invoke-LocalRcon 'cmend status'
  $core = Get-Core -Status $status
  $previousLocalMobSpawning = (Invoke-LocalRcon 'gamerule doMobSpawning').Trim()
  $mobSpawningMatch = [Regex]::Match($previousLocalMobSpawning,
    '(?i)doMobSpawning(?:\s*=\s*|\s+is\s+currently\s+set\s+to:\s*)(true|false)')
  if (-not $mobSpawningMatch.Success) { throw "Could not read doMobSpawning: $previousLocalMobSpawning" }
  $previousLocalMobSpawning = $mobSpawningMatch.Groups[1].Value.ToLowerInvariant()
  $naturalRegenerationOutput = (Invoke-LocalRcon 'gamerule naturalRegeneration').Trim()
  $naturalRegenerationMatch = [Regex]::Match($naturalRegenerationOutput,
    '(?i)naturalRegeneration(?:\s*=\s*|\s+is\s+currently\s+set\s+to:\s*)(true|false)')
  if (-not $naturalRegenerationMatch.Success) {
    throw "Could not read naturalRegeneration: $naturalRegenerationOutput"
  }
  $previousNaturalRegeneration = $naturalRegenerationMatch.Groups[1].Value.ToLowerInvariant()
  $null = Invoke-LocalRcon 'gamerule doMobSpawning false'
  $null = Invoke-LocalRcon 'gamerule naturalRegeneration false'
  $previousTrace = (Invoke-LocalRcon 'cmend debug trace status')
  $null = Invoke-LocalRcon 'cmend debug trace on'
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  Clear-LocalArenaAmbientMobs -Core $core

  foreach ($name in $names) {
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Value 'PASSIVE' -NoNewline -Encoding ASCII
    Start-Bot -Name $name -Core $core
  }
  Wait-BotsOnline
  # The shared bot helper starts its normal active loop once in the spawn
  # callback.  Flip the control file through ACTIVE and back to PASSIVE after
  # all joins so the helper's poller observes a real state transition and
  # cannot attack a guard before the capture-order assertion.
  foreach ($name in $names) { Set-BotMode -Name $name -Mode ACTIVE }
  Start-Sleep -Milliseconds 500
  foreach ($name in $names) { Set-BotMode -Name $name -Mode PASSIVE }
  Configure-Bots -Core $core
  $preCaptureHealth = Wait-Until -Description 'Wave 6 prisoner health query before capture' -Condition {
    try {
      $health = Get-PlayerHealth -Name $SecondBotName
      if ($health -gt 1.0D) { return $health }
    } catch { return $false }
    return $false
  }
  Write-Evidence "LIVE_WAVE6_PRE_CAPTURE_HEALTH player=$SecondBotName health=$preCaptureHealth"

  $script:waveOffset = Get-LogLength
  $null = Invoke-LocalRcon 'cmend test wave 6'
  $readyLog = Wait-Log -AfterOffset $waveOffset `
    -Pattern 'WAVE6_RITUAL_SPHERE_READY.*state=WAITING_FOR_PRISONER.*casters=4.*guards=12.*control_pairs=1.*drain_interval_ms=20000.*drain_hp=2.*health_floor=1.*authority=server'
  if ($readyLog -match 'END_RIFT_RINGS_READY|WAVE_6_PAIR_SPAWNED') {
    throw 'Legacy Collapse Rings appeared in the live Wave 6 log.'
  }
  Wait-Until -Description 'Wave 6 per-caster guarded diagnostics' -Condition {
    try {
      $snapshot = Get-AiJson
      $casters = if ($null -eq $snapshot.casters) { @() } else { @($snapshot.casters) }
      return $casters.Count -eq 4 -and [int]$snapshot.ritualGuards -eq 12 `
        -and [bool]$snapshot.ritualGuardOwnershipValid `
        -and @($casters | Where-Object { $_.state -ne 'GUARDED_CASTING' }).Count -eq 0
    } catch {
      return $false
    }
  } | Out-Null
  Assert-RitualCasterDiagnostics -ExpectedState 'GUARDED_CASTING' -ExpectedCasters 4 -ExpectedGuards 12 | Out-Null
  Write-Evidence 'LIVE_WAVE6_CASTER_GUARDED_PASS casters=4 guards=12 native_ai=false targets=0 ownership=true'
  $preCaptureTail = Get-LogTail -Offset $waveOffset
  # There must be no prisoner / not captured marker before the physical entry.
  if ($preCaptureTail -match 'WAVE6_RITUAL_PRISONER_CAPTURED') {
    throw 'Wave 6 captured a prisoner before either bot entered the ritual seal.'
  }
  Write-Evidence 'LIVE_WAVE6_WAITING_FOR_PRISONER_PASS outside_seal_verified=true auto_capture=false'

  # Bot A remains at the outer combat band.  Bot B alone crosses the visible
  # seal, while Bot A intentionally has the lexicographically smaller UUID.
  Teleport-Player -Name $SecondBotName -X ($core[0] + 0.5D) -Y $core[1] -Z ($core[2] + 0.5D)
  $script:captureOffset = $waveOffset
  $capturePattern = 'WAVE6_RITUAL_PRISONER_CAPTURED[^\r\n]*player=([0-9a-fA-F-]{32,36})[^\r\n]*first_drain_at=(\d+)'
  $captureTail = Wait-Log -AfterOffset $captureOffset `
    -Pattern $capturePattern -Seconds 30
  $captureMatch = [Regex]::Match($captureTail, $capturePattern)
  if (-not $captureMatch.Success -or $captureMatch.Groups[1].Value.Replace('-', '') -ne $secondUuid) {
    throw "The physical seal did not capture Bot B. expected=$secondUuid log=$captureTail"
  }
  $script:firstDrainAtMillis = [long]$captureMatch.Groups[2].Value
  $captureCount = [Regex]::Matches((Get-LogTail -Offset $captureOffset),
    'WAVE6_RITUAL_PRISONER_CAPTURED').Count
  if ($captureCount -ne 1) { throw "Expected exactly one prisoner capture, got $captureCount." }
  Write-Evidence "LIVE_WAVE6_CAPTURE_ORDER_PASS prisoner=$SecondBotName prisoner_uuid=$secondUuid free_lower_uuid=$firstUuid participants=4 casters=4 guards=12"

  Restart-LocalMinecraftForWave6 -Core $core
  Wait-Until -Description 'Wave 6 guarded caster state after restart' -Condition {
    try { return [int](Get-AiJson).ritualGuards -eq 12 } catch { return $false }
  } | Out-Null
  Assert-RitualCasterDiagnostics -ExpectedState 'GUARDED_CASTING' -ExpectedCasters 4 -ExpectedGuards 12 | Out-Null
  Write-Evidence 'LIVE_WAVE6_CASTER_RESTART_GUARDED_PASS casters=4 guards=12 native_ai=false ownership=true'

  $null = Invoke-LocalRcon ("attribute $SecondBotName minecraft:generic.max_health base set 5")
  $null = Invoke-LocalRcon ("effect clear $SecondBotName")
  $null = Invoke-LocalRcon ("effect give $SecondBotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon ("data merge entity $SecondBotName {Health:5.0f}")
  $null = Invoke-LocalRcon ("effect give $SecondBotName minecraft:instant_health 1 10 true")
  $healthAtCapture = Get-PlayerHealth -Name $SecondBotName
  if ($healthAtCapture -le 1.0D) { throw "Prisoner health was already at the ritual floor: $healthAtCapture" }

  # The capture marker is server-authored and carries the exact first-drain
  # deadline.  Wait until the 19.5-second equivalent (500 ms before that
  # deadline) rather than measuring
  # from the moment a slow RCON client happened to receive the log line.
  $nowMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $waitForNineteenFiveMillis = ($script:firstDrainAtMillis - 500L) - $nowMillis
  if ($waitForNineteenFiveMillis -gt 0L) {
    $nineteenFiveDeadline = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + $waitForNineteenFiveMillis
    Wait-Until -Description 'Wave 6 19.5 second drain boundary' -Seconds 30 -Condition {
      return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() -ge $nineteenFiveDeadline
    } | Out-Null
  }
  Assert-HealthEqual -Name $SecondBotName -Expected $healthAtCapture `
    -Reason 'prisoner health changed before the 20 second drain boundary' | Out-Null
  Write-Evidence "LIVE_WAVE6_DRAIN_19_5S_PASS health=$healthAtCapture unchanged=true"

  $appliedDrainPattern = 'WAVE6_RITUAL_PRISONER_DRAIN[^\r\n]*applied=true[^\r\n]*damage=(?:1\.9+|2(?:\.0+)?)[^\r\n]*drain_at=(\d+)'
  $appliedDrainCountBeforeFirst = [Regex]::Matches((Get-LogTail -Offset $captureOffset), $appliedDrainPattern).Count
  $appliedDrainLengthBeforeFirst = Get-LogLength
  $firstDrainLog = Wait-Log-MarkerIncrease -Pattern $appliedDrainPattern `
    -BeforeCount $appliedDrainCountBeforeFirst -BeforeLength $appliedDrainLengthBeforeFirst -Seconds 30
  $firstDrainMatches = [Regex]::Matches($firstDrainLog, $appliedDrainPattern)
  if ($firstDrainMatches.Count -lt 1) {
    throw 'The first post-restart ritual drain marker did not contain server drain_at.'
  }
  $firstDrainMatch = $firstDrainMatches[0]
  $script:firstDrainAtMillis = [long]$firstDrainMatch.Groups[1].Value
  $healthAfterFirstDrain = Get-PlayerHealth -Name $SecondBotName
  if ([Math]::Abs($healthAfterFirstDrain - ($healthAtCapture - 2.0D)) -gt 0.01D) {
    throw "First ritual drain was not exactly 2 HP: before=$healthAtCapture after=$healthAfterFirstDrain"
  }
  $appliedDrainsAfterFirst = Get-AppliedRitualDrainCount -Offset $script:captureOffset
  Write-Evidence "LIVE_WAVE6_DRAIN_20S_PASS health_before=$healthAtCapture health_after=$healthAfterFirstDrain damage=2"

  # Representative external damage is now applied in the interval after the
  # first drain and before the floor drain.  This keeps the cadence assertion
  # independent of RCON latency while still proving non-ritual damage cannot
  # change the captured player's health.
  Assert-ExternalDamageIgnored -ExpectedHealth $healthAfterFirstDrain

  # The second boundary reaches the 1 HP floor.  This intentionally uses a
  # five-HP disposable prisoner so the floor can be observed in one probe.
  $secondDrainAtMillis = $script:firstDrainAtMillis + 20000L
  $nowMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $waitForSecondDrainMillis = ($secondDrainAtMillis - 500L) - $nowMillis
  if ($waitForSecondDrainMillis -gt 0L) {
    $secondDrainDeadline = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + $waitForSecondDrainMillis
    Wait-Until -Description 'Wave 6 second drain boundary' -Seconds 30 -Condition {
      return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() -ge $secondDrainDeadline
    } | Out-Null
  }
  $drainsBeforeSecondDeadline = Get-AppliedRitualDrainCount -Offset $captureOffset
  if ($drainsBeforeSecondDeadline -ne $appliedDrainsAfterFirst) {
    throw "A second ritual drain appeared before its server deadline: before=$appliedDrainsAfterFirst after=$drainsBeforeSecondDeadline"
  }
  Assert-HealthEqual -Name $SecondBotName -Expected $healthAfterFirstDrain `
    -Reason 'prisoner health changed before the second 20 second drain boundary' | Out-Null
  Write-Evidence "LIVE_WAVE6_DRAIN_39_5S_PASS health=$healthAfterFirstDrain applied_drains=$drainsBeforeSecondDeadline unchanged=true"
  $secondDrainLog = Wait-Log -AfterOffset $captureOffset `
    -Pattern 'WAVE6_RITUAL_PRISONER_DRAIN[^\r\n]*applied=true[^\r\n]*remaining=1(?:\.0+)?' -Seconds 10
  $healthAtFloor = Get-PlayerHealth -Name $SecondBotName
  Assert-HealthEqual -Name $SecondBotName -Expected 1.0D `
    -Reason 'ritual drain did not stop at the 1 HP floor' | Out-Null
  Write-Evidence 'LIVE_WAVE6_DRAIN_FLOOR_PASS remaining=1 intensity_not_increased=true'

  Assert-ExternalDamageIgnored -ExpectedHealth 1.0D
  $drainCountAtFloor = Get-AppliedRitualDrainCount -Offset $captureOffset
  $floorDeadline = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + 20000L
  Wait-Until -Description 'Wave 6 no extra floor drain interval' -Seconds 25 -Condition {
    return [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() -ge $floorDeadline
  } | Out-Null
  if ((Get-AppliedRitualDrainCount -Offset $captureOffset) -ne $drainCountAtFloor) {
    throw 'An extra ritual drain was applied after the prisoner reached the 1 HP floor.'
  }
  Assert-HealthEqual -Name $SecondBotName -Expected 1.0D `
    -Reason 'additional drain changed health below the 1 HP floor' | Out-Null

  # Exercise each authored core ability through the real server methods.  The
  # hook is deliberately console-only and local/staging-only inside the
  # plugin; using it here keeps this acceptance test deterministic without
  # changing guard health or relying on a disposable client to defeat four
  # spatially separated guard groups within a wall-clock timeout.
  Set-BotMode -Name $FirstBotName -Mode PASSIVE
  Set-BotMode -Name $ThirdBotName -Mode PASSIVE
  $projectileOffset = Get-LogLength
  $projectileResponse = Invoke-LocalRcon 'cmend test ritual force projectile'
  if ($projectileResponse -notmatch '(?i)forced\s+PROJECTILE_CASTER') {
    throw "Projectile local hook was not acknowledged: $projectileResponse"
  }
  $projectileAbilityPattern = 'WAVE6_RITUAL_ABILITY[^\r\n]*role=PROJECTILE_CASTER[^\r\n]*source=LOCAL_TEST_HOOK'
  $projectileAbilityLog = Wait-Log -AfterOffset $projectileOffset `
    -Pattern $projectileAbilityPattern -Seconds $TimeoutSeconds
  $projectileAbilityLine = [Regex]::Match($projectileAbilityLog, $projectileAbilityPattern).Value
  $projectileEventMatch = [Regex]::Match($projectileAbilityLine, 'event=([0-9a-fA-F-]{32,36})')
  $projectileGenerationMatch = [Regex]::Match($projectileAbilityLine, 'generation=(\d+)')
  $projectileCasterMatch = [Regex]::Match($projectileAbilityLine, 'caster=([0-9a-fA-F-]{32,36})')
  if (-not $projectileEventMatch.Success -or -not $projectileGenerationMatch.Success -or
      -not $projectileCasterMatch.Success) {
    throw "Projectile ability marker did not carry event, generation, and caster: $projectileAbilityLine"
  }
  $originPattern = 'WAVE6_RITUAL_PROJECTILE_ORIGIN_ASSERT[^\r\n]*event=' +
    [Regex]::Escape($projectileEventMatch.Groups[1].Value) +
    '[^\r\n]*generation=' + [Regex]::Escape($projectileGenerationMatch.Groups[1].Value) +
    '[^\r\n]*caster=' + [Regex]::Escape($projectileCasterMatch.Groups[1].Value) +
    '[^\r\n]*target=' + $freeUuidPattern +
    '[^\r\n]*sphere_origin=[^\r\n]*projectile_spawn=[^\r\n]*origin_distance=(?:0|0\.0+)[^\r\n]*passed=true'
  $originLog = Wait-Log -AfterOffset $projectileOffset `
    -Pattern $originPattern -Seconds $TimeoutSeconds
  if ($originLog -match 'caster=[^\r\n]*sphere_origin=') {
    Write-Evidence 'LIVE_WAVE6_PROJECTILE_ORIGIN_PASS sphere_origin=true projectile_spawn=true max_distance=0.25 caster_origin=false'
  } else {
    throw "Sphere projectile origin marker was incomplete: $originLog"
  }

  $zoneOffset = Get-LogLength
  $zoneResponse = Invoke-LocalRcon 'cmend test ritual force zone'
  if ($zoneResponse -notmatch '(?i)forced\s+ZONE_CASTER') {
    throw "Zone local hook was not acknowledged: $zoneResponse"
  }
  $zoneAbilityPattern = 'WAVE6_RITUAL_ABILITY[^\r\n]*role=ZONE_CASTER[^\r\n]*source=LOCAL_TEST_HOOK'
  Wait-Log -AfterOffset $zoneOffset `
    -Pattern $zoneAbilityPattern -Seconds $TimeoutSeconds | Out-Null
  $zoneLog = Wait-Log -AfterOffset $zoneOffset `
    -Pattern 'WAVE6_RITUAL_ZONE_TELEGRAPH[^\r\n]*zone=([0-9a-fA-F-]{32,36})[^\r\n]*center=\S+\s+-?\d+,-?\d+,-?\d+[^\r\n]*size=4' -Seconds $TimeoutSeconds
  $zoneMatch = [Regex]::Match($zoneLog,
    'WAVE6_RITUAL_ZONE_TELEGRAPH[^\r\n]*zone=([0-9a-fA-F-]{32,36})[^\r\n]*center=')
  if (-not $zoneMatch.Success) { throw "Zone target UUID was not recorded: $zoneLog" }
  $freeNames = @($FirstBotName, $ThirdBotName, $FourthBotName)
  $zoneTargetName = $null
  foreach ($freeName in $freeNames) {
    if ((Get-OfflinePlayerUuid -Name $freeName) -eq $zoneMatch.Groups[1].Value.Replace('-', '')) {
      $zoneTargetName = $freeName
    }
  }
  if ($null -eq $zoneTargetName) { throw "Zone targeted a non-free UUID: $($zoneMatch.Groups[1].Value)" }
  Set-BotMode -Name $zoneTargetName -Mode PASSIVE
  $zoneCenter = Parse-LoggedCenter -Text $zoneLog
  Teleport-Player -Name $zoneTargetName -X $zoneCenter[0] -Y $zoneCenter[1] -Z $zoneCenter[2]
  $zoneEffects = Wait-Until -Description 'Wave 6 zone effects on free target' -Condition {
    $effects = Get-PlayerEffects -Name $zoneTargetName
    if ($effects -match 'wither' -and $effects -match 'slowness') { return $effects }
    return $false
  }
  $prisonerEffects = Get-PlayerEffects -Name $SecondBotName
  # Expected active effects are WITHER and SLOWNESS.  POISON is explicitly
  # forbidden by the Ritual Sphere contract.
  if ($zoneEffects -notmatch 'wither' -or $zoneEffects -notmatch 'slowness' -or
      $zoneEffects -match 'poison') {
    throw "Free zone effects were wrong: $zoneEffects"
  }
  if ($prisonerEffects -match 'wither|slowness|poison') {
    throw "Prisoner received a zone effect: $prisonerEffects"
  }
  Write-Evidence "LIVE_WAVE6_ZONE_EFFECTS_PASS target=$zoneTargetName wither=true slowness=true reverse_expected=true poison=false prisoner_zone_effects=false"

  # Move the zone recipient out before testing the standalone reverse ability,
  # then clear the local control state so the following swap can form its one
  # pair from the two free participants.
  Teleport-Player -Name $zoneTargetName -X ($core[0] + 6.0D) -Y $core[1] -Z ($core[2] + 0.5D)
  $null = Invoke-LocalRcon 'cmend test ritual controls clear'
  $reverseOffset = Get-LogLength
  $reverseResponse = Invoke-LocalRcon 'cmend test ritual force reverse'
  if ($reverseResponse -notmatch '(?i)forced\s+REVERSE_CASTER') {
    throw "Reverse local hook was not acknowledged: $reverseResponse"
  }
  $reverseAbilityPattern = 'WAVE6_RITUAL_ABILITY[^\r\n]*role=REVERSE_CASTER[^\r\n]*source=LOCAL_TEST_HOOK'
  Wait-Log -AfterOffset $reverseOffset `
    -Pattern $reverseAbilityPattern -Seconds $TimeoutSeconds | Out-Null
  $reversePrisonerPattern = 'WAVE6_RITUAL_CONTROL[^\r\n]*mode=REVERSE[^\r\n]*action=START[^\r\n]*player=' + $secondUuidPattern
  $reverseFreePattern = 'WAVE6_RITUAL_CONTROL[^\r\n]*mode=REVERSE[^\r\n]*action=START[^\r\n]*player=' + $freeUuidPattern
  $reverseLog = Wait-Log -AfterOffset $reverseOffset `
    -Pattern $reverseFreePattern -Seconds $TimeoutSeconds
  if ($reverseLog -match $reversePrisonerPattern) {
    throw "Ritual reverse targeted the prisoner: $reverseLog"
  }
  $null = Invoke-LocalRcon 'cmend test ritual controls clear'
  Start-Sleep -Milliseconds 500
  $swapOffset = Get-LogLength
  $swapResponse = Invoke-LocalRcon 'cmend test ritual force swap'
  if ($swapResponse -notmatch '(?i)forced\s+CONTROL_SWAP_CASTER') {
    throw "Swap local hook was not acknowledged: $swapResponse"
  }
  $swapAbilityPattern = 'WAVE6_RITUAL_ABILITY[^\r\n]*role=CONTROL_SWAP_CASTER[^\r\n]*source=LOCAL_TEST_HOOK'
  Wait-Log -AfterOffset $swapOffset `
    -Pattern $swapAbilityPattern -Seconds $TimeoutSeconds | Out-Null
  $swapFreePairPattern = 'WAVE6_RITUAL_CONTROL[^\r\n]*mode=SWAP[^\r\n]*action=START[^\r\n]*first=' +
    $freeUuidPattern + '[^\r\n]*second=' + $freeUuidPattern + '[^\r\n]*prisoner=' + $secondUuidPattern
  $swapLog = Wait-Log -AfterOffset $swapOffset `
    -Pattern $swapFreePairPattern -Seconds $TimeoutSeconds
  $swapLine = [Regex]::Matches($swapLog, $swapFreePairPattern) | Select-Object -Last 1
  if ($null -eq $swapLine) { throw "Swap control marker was not captured: $swapLog" }
  $swapText = $swapLine.Value
  $swapIds = [Regex]::Match($swapText, 'first=([0-9a-fA-F-]{36,36})[^\r\n]*second=([0-9a-fA-F-]{36,36})[^\r\n]*prisoner=')
  if (-not $swapIds.Success -or $swapIds.Groups[1].Value -eq $swapIds.Groups[2].Value) {
    throw "Swap control selected an invalid pair: $swapText"
  }
  Write-Evidence 'LIVE_WAVE6_ABILITY_ROLES_PASS projectile=server sphere zone=4x4 reverse=server control_swap=server'
  Write-Evidence 'LIVE_WAVE6_FREE_TARGET_CONTROL_PASS reverse=true swap=true prisoner_excluded=true reverse_swap_mutex=true'

  $exposedResponse = Invoke-LocalRcon 'cmend test ritual caster exposed'
  if ($exposedResponse -notmatch '(?i)state=exposed') {
    throw "Wave 6 exposed-caster hook was not acknowledged: $exposedResponse"
  }
  Wait-Until -Description 'Wave 6 exposed caster state' -Condition {
    try {
      $snapshot = Get-AiJson
      $casters = @($snapshot.casters)
      return $casters.Count -eq 4 -and [bool]$snapshot.ritualGuardOwnershipValid `
        -and @($casters | Where-Object { $_.state -eq 'EXPOSED_CASTING' -and [int]$_.guardCount -eq 0 `
          -and -not [bool]$_.nativeAiEnabled -and $null -eq $_.target }).Count -eq 1
    } catch { return $false }
  } | Out-Null
  Write-Evidence 'LIVE_WAVE6_CASTER_EXPOSED_PASS caster_count=1 guards_removed=3 native_ai=false target=none ownership=true'

  $awakenedResponse = Invoke-LocalRcon 'cmend test ritual caster awakened'
  if ($awakenedResponse -notmatch '(?i)state=awakened') {
    throw "Wave 6 awakened-caster hook was not acknowledged: $awakenedResponse"
  }
  Wait-Until -Description 'Wave 6 mixed awakened caster state' -Condition {
    try {
      $snapshot = Get-AiJson
      $casters = @($snapshot.casters)
      return $casters.Count -eq 4 -and [bool]$snapshot.ritualGuardOwnershipValid `
        -and @($casters | Where-Object { $_.state -eq 'AWAKENED_ATTACKING' `
          -and [bool]$_.nativeAiEnabled -and [bool]$_.canTargetPlayers }).Count -eq 1 `
        -and @($casters | Where-Object { $_.state -eq 'GUARDED_CASTING' }).Count -ge 1
    } catch { return $false }
  } | Out-Null
  Write-Evidence 'LIVE_WAVE6_CASTER_AWAKENED_PASS caster_count=1 native_ai=true target_allowed=true mixed_state=true ownership=true'

  $completionOffset = Get-LogLength
  $null = Invoke-LocalRcon 'cmend test ritual complete'
  $cleanupPattern = 'WAVE6_RITUAL_COMPLETE[^\r\n]*cleanup=server[^\r\n]*sphere=false[^\r\n]*zones=0[^\r\n]*controls=0[^\r\n]*beams=0[^\r\n]*projectiles=0[^\r\n]*prisoner_tag=cleared'
  $cleanupLog = Wait-Log -AfterOffset $completionOffset -Pattern $cleanupPattern -Seconds $TimeoutSeconds
  $cleanupState = Wait-Until -Description 'Wave 6 server cleanup zero state' -Condition {
    $status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
    $objectives = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
    if ($status -match 'event-mobs=\s*0' -and $objectives -match 'visuals=\s*0') {
      return @($status, $objectives)
    }
    return $false
  }
  $finalStatus = $cleanupState[0]
  $objectiveStatus = $cleanupState[1]
  if ($finalStatus -notmatch 'event-mobs=\s*0') {
    # The accepted status form is event-mobs=0 after server cleanup.
    throw "Wave 6 completion left event mobs: $finalStatus"
  }
  if ($objectiveStatus -notmatch 'visuals=\s*0') {
    throw "Wave 6 completion left visual displays: $objectiveStatus"
  }
  Write-Evidence 'LIVE_WAVE6_COMPLETION_CLEANUP_PASS sphere=false beams=0 zones=0 controls=0 prisoner_released=true prisoner_tag_removed=true transient_entities=0'
}
finally {
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { $cleanupFailures.Add('wave clear: ' + $_.Exception.Message) }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { $cleanupFailures.Add('boss cleanup: ' + $_.Exception.Message) }
  if ($null -ne $previousTrace -and $previousTrace -match '(?i)Combat Trace:\s*ON') {
    try { $null = Invoke-LocalRcon 'cmend debug trace on' } catch { $cleanupFailures.Add('trace restore on: ' + $_.Exception.Message) }
  } else {
    try { $null = Invoke-LocalRcon 'cmend debug trace off' } catch { $cleanupFailures.Add('trace restore off: ' + $_.Exception.Message) }
  }
  if ($null -ne $previousLocalMobSpawning) {
    try { $null = Invoke-LocalRcon ("gamerule doMobSpawning $previousLocalMobSpawning") } catch { $cleanupFailures.Add('doMobSpawning restore: ' + $_.Exception.Message) }
  }
  if ($null -ne $previousNaturalRegeneration) {
    try { $null = Invoke-LocalRcon ("gamerule naturalRegeneration $previousNaturalRegeneration") } catch { $cleanupFailures.Add('naturalRegeneration restore: ' + $_.Exception.Message) }
  }
  Stop-Bots
  if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
    try { $serverProcess.Kill() } catch { $cleanupFailures.Add('server process kill: ' + $_.Exception.Message) }
  }
  if ($null -eq $previousBotPassword) {
    if (Test-Path Env:END_RIFT_BOT_PASSWORD) {
      try { Remove-Item Env:END_RIFT_BOT_PASSWORD -ErrorAction Stop } catch { $cleanupFailures.Add('bot password env cleanup: ' + $_.Exception.Message) }
    }
  } else {
    $env:END_RIFT_BOT_PASSWORD = $previousBotPassword
  }
  if ($null -eq $previousSkipRegister) {
    if (Test-Path Env:END_RIFT_BOT_SKIP_REGISTER) {
      try { Remove-Item Env:END_RIFT_BOT_SKIP_REGISTER -ErrorAction Stop } catch { $cleanupFailures.Add('skip register env cleanup: ' + $_.Exception.Message) }
    }
  } else {
    $env:END_RIFT_BOT_SKIP_REGISTER = $previousSkipRegister
  }
  if (Test-Path -LiteralPath $controlDirectory -PathType Container) {
    try { Remove-Item -LiteralPath $controlDirectory -Recurse -Force -ErrorAction Stop } catch { $cleanupFailures.Add('control directory cleanup: ' + $_.Exception.Message) }
  }
  try {
    $finalStatus = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
    $finalObjectives = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
    $finalAi = Get-AiJson
    if (($finalStatus -notmatch 'event-mobs=\s*0' -or $finalObjectives -notmatch 'visuals=\s*0' `
        -or [int]$finalAi.mobile -ne 0 -or [bool]$finalAi.bossPresent `
        -or [int]$finalAi.ritualCasters -ne 0 -or [int]$finalAi.ritualGuards -ne 0)) {
      throw "cleanup residue status=$finalStatus objectives=$finalObjectives ai=$($finalAi | ConvertTo-Json -Depth 12 -Compress)"
    }
    Write-Evidence 'LIVE_WAVE6_CLEANUP_ZERO_STATE_PASS event_mobs=0 mobile=0 boss=false casters=0 guards=0 visuals=0'
  } catch {
    $cleanupFailures.Add('cleanup zero-state query: ' + $_.Exception.Message)
  }
  if ($serverStartedByProbe) {
    try { $null = Invoke-LocalRcon 'stop' } catch { $cleanupFailures.Add('server stop: ' + $_.Exception.Message) }
  }
  if ($cleanupFailures.Count -gt 0) {
    throw ('Wave 6 cleanup failed: ' + ($cleanupFailures -join ' | '))
  }
}
