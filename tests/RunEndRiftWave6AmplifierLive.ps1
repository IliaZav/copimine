[CmdletBinding()]
param(
  [ValidateRange(60, 300)]
  [int]$BotDurationSeconds = 180,
  [ValidateRange(30, 180)]
  [int]$TimeoutSeconds = 120
)

# Local/staging-only Paper acceptance probe for the real Wave 6 amplifier
# overlay.  Nine real offline Mineflayer clients are required because the
# bounded 2-20 player table creates its first AMPLIFIER caster at 9 players.
# The probe leaves the isolated local server running for manual inspection.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$pluginSource = Join-Path $root 'copimine-end-event\CopiMineEndEvent.jar'
$pluginTarget = Join-Path $serverDir 'plugins\CopiMineEndEvent.jar'
$controlDirectory = Join-Path $runtimeRoot ('wave6-amplifier-control-' + (Get-Date -Format 'yyyyMMddHHmmssfff'))
$evidencePath = Join-Path $runtimeRoot ('wave6-amplifier-live-' + (Get-Date -Format 'yyyyMMddHHmmssfff') + '.log')
$names = @(
  'EndRiftAmpA', 'EndRiftAmpB', 'EndRiftAmpC', 'EndRiftAmpD', 'EndRiftAmpE',
  'EndRiftAmpF', 'EndRiftAmpG', 'EndRiftAmpH', 'EndRiftAmpI'
)
$botProcesses = [System.Collections.Generic.List[object]]::new()
$whitelistedNames = [System.Collections.Generic.List[string]]::new()
$previousMobSpawning = $null
$previousNaturalRegeneration = $null
$previousBotPassword = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_PASSWORD', 'Process')
$previousSkipRegister = [Environment]::GetEnvironmentVariable('END_RIFT_BOT_SKIP_REGISTER', 'Process')
$cleanupFailures = [System.Collections.Generic.List[string]]::new()

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
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

function Test-PortOpen([int]$Port) {
  $client = [Net.Sockets.TcpClient]::new()
  try {
    $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
    if (-not $async.AsyncWaitHandle.WaitOne(700)) { return $false }
    $client.EndConnect($async)
    return $true
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
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
      $value = & $Condition
      if ($value) { return $value }
    } catch {
      # A command can observe a tick boundary while an entity is being removed.
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for condition: $Description"
}

function Read-PaperLog { return [string](Get-Content -LiteralPath $paperLog -Raw) }
function Get-LogLength { return [int64](Read-PaperLog).Length }
function Get-LogTail([int64]$Offset) {
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

function Get-AiJson {
  $raw = (Invoke-LocalRcon 'cmend debug ai --json') -replace '\u00A7.', ''
  try { return ($raw | ConvertFrom-Json -ErrorAction Stop) }
  catch { throw "Wave 6 amplifier AI diagnostics were not valid JSON: $raw" }
}

function Get-Core([string]$Status) {
  $match = [Regex]::Match(($Status -replace '\u00A7.', ''), 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core is missing from status: $Status" }
  return [int[]]@([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Teleport-Player {
  param([string]$Name, [double]$X, [double]$Y, [double]$Z)
  # Essentials owns the unqualified /tp command on this server.  The
  # namespace-qualified vanilla command avoids an alias or destination-world
  # interpretation changing the probe's exact seal position.
  $null = Invoke-LocalRcon ("minecraft:tp $Name $X $Y $Z")
}

function Wait-RitualPrisonerCapture {
  param(
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [Parameter(Mandatory = $true)][string]$PlayerName,
    [Parameter(Mandatory = $true)][int[]]$Core,
    [int]$Seconds = 30
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Get-LogTail -Offset $AfterOffset
    if ($tail -match 'WAVE6_RITUAL_PRISONER_CAPTURED') { return $tail }
    # Dense Wave 6 mobs can physically push a passive client away between
    # ticks.  Re-apply the same server-authoritative seal position until the
    # capture tick has accepted it; this does not alter gameplay code.
    Teleport-Player -Name $PlayerName -X ($Core[0] + 0.5D) -Y $Core[1] -Z ($Core[2] + 0.5D)
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for 'WAVE6_RITUAL_PRISONER_CAPTURED'. See $paperLog"
}

function Start-Bot([string]$Name, [int[]]$Core) {
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $output = Join-Path $runtimeRoot ($Name + '-wave6-amplifier.log')
  $error = Join-Path $runtimeRoot ($Name + '-wave6-amplifier.err.log')
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 6.0D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 250 "' + $controlDirectory + '"'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $output -RedirectStandardError $error -WindowStyle Hidden -PassThru
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

function Start-LocalMinecraftIfNeeded {
  if (-not (Test-PortOpen 25576)) {
    & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tests\StartEndRiftLocalUserSession.ps1') `
      -AdminNickname SudoKillDash9
    if ($LASTEXITCODE -ne 0) { throw 'Local Paper startup failed.' }
  } else {
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginSource).Hash
    $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginTarget).Hash
    if ($sourceHash -ne $targetHash) {
      $null = Invoke-LocalRcon 'stop'
      Wait-Until -Seconds 90 -Description 'old local Paper process to stop' -Condition { -not (Test-PortOpen 25576) } | Out-Null
      & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tests\StartEndRiftLocalUserSession.ps1') `
        -AdminNickname SudoKillDash9
      if ($LASTEXITCODE -ne 0) { throw 'Local Paper restart failed.' }
    }
  }
  Wait-Until -Seconds 90 -Description 'local Paper RCON port' -Condition { Test-PortOpen 25576 } | Out-Null
  $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginSource).Hash
  $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pluginTarget).Hash
  if ($sourceHash -ne $targetHash) { throw "Local plugin hash mismatch: source=$sourceHash target=$targetHash" }
}

if (@($names | Select-Object -Unique).Count -ne $names.Count) { throw 'Amplifier bot names must be unique.' }
$configText = Get-Content -LiteralPath $configPath -Raw
if ($configText -notmatch '(?m)^environment:\s*(local|staging)\s*$') {
  throw 'Refused: Wave 6 amplifier probe requires environment=local|staging.'
}
$properties = Get-Content -LiteralPath $propertiesPath -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Refused: amplifier probe requires isolated ports 25566/25576.'
}
New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null

try {
  Start-LocalMinecraftIfNeeded
  $status = Invoke-LocalRcon 'cmend status'
  $core = Get-Core -Status $status

  $mobRule = (Invoke-LocalRcon 'gamerule doMobSpawning').Trim()
  $mobMatch = [Regex]::Match($mobRule, '(?i)(true|false)\s*$')
  if (-not $mobMatch.Success) { throw "Could not read doMobSpawning: $mobRule" }
  $previousMobSpawning = $mobMatch.Groups[1].Value.ToLowerInvariant()
  $regenRule = (Invoke-LocalRcon 'gamerule naturalRegeneration').Trim()
  $regenMatch = [Regex]::Match($regenRule, '(?i)(true|false)\s*$')
  if (-not $regenMatch.Success) { throw "Could not read naturalRegeneration: $regenRule" }
  $previousNaturalRegeneration = $regenMatch.Groups[1].Value.ToLowerInvariant()
  $null = Invoke-LocalRcon 'gamerule doMobSpawning false'
  $null = Invoke-LocalRcon 'gamerule naturalRegeneration false'
  $null = Invoke-LocalRcon 'cmend debug trace on'
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'

  # Generate an ephemeral AuthMe credential for this disposable probe.  Do
  # not commit a reusable password into the live-runner source or its logs.
  $env:END_RIFT_BOT_PASSWORD = [Guid]::NewGuid().ToString('N')
  if (-not (Test-Path Env:END_RIFT_BOT_SKIP_REGISTER)) { Remove-Item Env:END_RIFT_BOT_SKIP_REGISTER -ErrorAction SilentlyContinue }
  foreach ($name in $names) {
    $null = Invoke-LocalRcon ("whitelist add $name")
    $whitelistedNames.Add($name)
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Value 'PASSIVE' -NoNewline -Encoding ASCII
    Start-Bot -Name $name -Core $core
  }
  Wait-Until -Seconds 90 -Description 'nine amplifier probe players online' -Condition {
    $list = Invoke-LocalRcon 'list'
    return @($names | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0
  } | Out-Null
  Start-Sleep -Seconds 2
  foreach ($name in $names) {
    $null = Invoke-LocalRcon ("gamemode survival $name")
    $null = Invoke-LocalRcon ("effect clear $name")
    $null = Invoke-LocalRcon ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon ("data merge entity $name {Health:20.0f}")
    Teleport-Player -Name $name -X ($core[0] + 6.0D) -Y $core[1] -Z ($core[2] + 0.5D)
  }

  $waveOffset = Get-LogLength
  $null = Invoke-LocalRcon 'cmend test wave 6'
  $readyTail = Wait-Log -AfterOffset $waveOffset `
    -Pattern 'WAVE6_RITUAL_SPHERE_READY[^\r\n]*state=WAITING_FOR_PRISONER[^\r\n]*casters=5[^\r\n]*guards=15[^\r\n]*control_pairs=2[^\r\n]*authority=server'
  if ($readyTail -match 'END_RIFT_RINGS_READY|WAVE_6_PAIR_SPAWNED') {
    throw 'Legacy Collapse Rings appeared during the amplifier probe.'
  }
  $snapshot = Wait-Until -Description 'one guarded Wave 6 amplifier caster' -Condition {
    try {
      $current = Get-AiJson
      $casters = @($current.casters)
      $amplifiers = @($casters | Where-Object { $_.role -eq 'AMPLIFIER' -and $_.state -eq 'GUARDED_CASTING' })
      if ($casters.Count -eq 5 -and [int]$current.ritualGuards -eq 15 -and $amplifiers.Count -eq 1) {
        return $current
      }
    } catch { }
    return $false
  }
  $amplifier = @($snapshot.casters | Where-Object { $_.role -eq 'AMPLIFIER' })[0]
  $amplifierUuid = [string]$amplifier.id
  $amplifierGuardUuids = @($amplifier.guardIds | ForEach-Object { [string]$_ })
  if ([string]::IsNullOrWhiteSpace($amplifierUuid)) { throw 'Amplifier UUID was missing from AI diagnostics.' }
  if ($amplifierGuardUuids.Count -lt 1) { throw 'Amplifier guard UUIDs were missing from AI diagnostics.' }
  Write-Evidence "LIVE_WAVE6_AMPLIFIER_CASTER_READY role=AMPLIFIER state=$($amplifier.state) guards=$($amplifier.guardCount) uuid=$amplifierUuid"

  Teleport-Player -Name $names[0] -X ($core[0] + 0.5D) -Y $core[1] -Z ($core[2] + 0.5D)
  $captureTail = Wait-RitualPrisonerCapture -AfterOffset $waveOffset `
    -PlayerName $names[0] -Core $core -Seconds 30
  if ($captureTail -notmatch 'WAVE6_RITUAL_PRISONER_CAPTURED') { throw 'Amplifier probe prisoner capture did not occur.' }
  # Hold the disposable prisoner at the ritual floor so the before/after
  # amplifier comparison is not confounded by a 20-second drain boundary.
  $null = Invoke-LocalRcon ("data merge entity $($names[0]) {Health:1.0f}")
  $drainHoldResponse = Invoke-LocalRcon 'cmend test ritual drain hold'
  if ($drainHoldResponse -notmatch 'LOCAL_TEST_HOOK drain=hold') {
    throw "Could not hold the disposable ritual drain clock: $drainHoldResponse"
  }
  Write-Evidence "LOCAL_TEST_HOOK drain=hold response=$drainHoldResponse"

  $amplifiedOffset = Get-LogLength
  $amplifiedAbilityPattern = 'WAVE6_RITUAL_ABILITY[^\r\n]*role=PROJECTILE_CASTER[^\r\n]*state=(?:GUARDED_CASTING|EXPOSED_CASTING)[^\r\n]*source=NATURAL[^\r\n]*amplifier_count=1[^\r\n]*successful_drains=(\d+)[^\r\n]*effective_projectiles=4[^\r\n]*effective_intensity=(\d+)[^\r\n]*cooldown_ms=(\d+)'
  $amplifiedTail = Wait-Log -AfterOffset $amplifiedOffset -Pattern $amplifiedAbilityPattern
  $amplifiedLine = ([Regex]::Matches($amplifiedTail, $amplifiedAbilityPattern) | Select-Object -Last 1).Value
  $amplifiedMatch = [Regex]::Match($amplifiedLine, $amplifiedAbilityPattern)
  $amplifiedDrains = [int]$amplifiedMatch.Groups[1].Value
  $amplifiedIntensity = [int]$amplifiedMatch.Groups[2].Value
  $amplifiedCooldown = [long]$amplifiedMatch.Groups[3].Value
  if ($amplifiedCooldown -le 0) { throw "Amplified cooldown was not readable: $amplifiedLine" }
  $amplifiedZonePattern = 'WAVE6_RITUAL_ZONE_TELEGRAPH[^\r\n]*amplifier_count=1[^\r\n]*duration_ms=(\d+)'
  $amplifiedZoneTail = Wait-Log -AfterOffset $amplifiedOffset -Pattern $amplifiedZonePattern
  $amplifiedDuration = [long]([Regex]::Match($amplifiedZoneTail, $amplifiedZonePattern).Groups[1].Value)
  if ($amplifiedDuration -le 0) { throw "Amplified zone duration was not readable: $amplifiedZoneTail" }

  $lossOffset = Get-LogLength
  # The ritual shield correctly rejects damage while the amplifier still has
  # guards.  Remove that caster's real guards first, then remove the exposed
  # caster by its direct UUID.  Essentials owns unqualified /kill, so always
  # use the vanilla namespace for these entity-targeted test commands.
  $guardKillResponses = [System.Collections.Generic.List[string]]::new()
  foreach ($guardUuid in $amplifierGuardUuids) {
    $guardKillResponses.Add((Invoke-LocalRcon ("minecraft:kill $guardUuid")))
  }
  $exposedSnapshot = Wait-Until -Description 'amplifier guard removal and exposed state' -Condition {
    try {
      $current = Get-AiJson
      $currentAmplifier = @($current.casters | Where-Object { $_.role -eq 'AMPLIFIER' })[0]
      return $null -ne $currentAmplifier -and [int]$currentAmplifier.guardCount -eq 0 -and
        [string]$currentAmplifier.state -eq 'EXPOSED_CASTING'
    } catch { return $false }
  }
  # Wait-Until returns the boolean condition result for this probe.  Query the
  # live diagnostics once more so the evidence line records the actual state,
  # not the condition's $true value.
  $exposedSnapshot = Get-AiJson
  $exposedCaster = @($exposedSnapshot.casters | Where-Object { $_.role -eq 'AMPLIFIER' })[0]
  $exposedState = [string]$exposedCaster.state
  Write-Evidence "LIVE_WAVE6_AMPLIFIER_EXPOSED_PASS guards=0 state=$exposedState"
  $killResponse = Invoke-LocalRcon ("minecraft:kill $amplifierUuid")
  Write-Evidence "LIVE_WAVE6_AMPLIFIER_KILL_COMMAND response=$killResponse"
  if ($killResponse -match '(?i)Игрок не найден|player was not found|no entity was found|No entity was found') {
    throw "Amplifier kill command did not select the live entity: $killResponse"
  }
  $snapshotAfterLoss = Wait-Until -Description 'amplifier removal from live AI ownership' -Condition {
    try {
      $current = Get-AiJson
      $casters = @($current.casters)
      return $casters.Count -eq 4 -and @($casters | Where-Object { $_.role -eq 'AMPLIFIER' }).Count -eq 0
    } catch { return $false }
  }
  Write-Evidence "LIVE_WAVE6_AMPLIFIER_LOSS_PASS removed=true before=1 after=0 caster_count=$(@($snapshotAfterLoss.casters).Count)"

  $baselineAbilityPattern = 'WAVE6_RITUAL_ABILITY[^\r\n]*role=PROJECTILE_CASTER[^\r\n]*state=(?:GUARDED_CASTING|EXPOSED_CASTING)[^\r\n]*source=NATURAL[^\r\n]*amplifier_count=0[^\r\n]*successful_drains=(\d+)[^\r\n]*effective_projectiles=3[^\r\n]*effective_intensity=(\d+)[^\r\n]*cooldown_ms=(\d+)'
  $baselineTail = Wait-Log -AfterOffset $lossOffset -Pattern $baselineAbilityPattern
  $baselineLine = ([Regex]::Matches($baselineTail, $baselineAbilityPattern) | Select-Object -Last 1).Value
  $baselineMatch = [Regex]::Match($baselineLine, $baselineAbilityPattern)
  $baselineDrains = [int]$baselineMatch.Groups[1].Value
  $baselineIntensity = [int]$baselineMatch.Groups[2].Value
  $baselineCooldown = [long]$baselineMatch.Groups[3].Value
  $baselineZonePattern = 'WAVE6_RITUAL_ZONE_TELEGRAPH[^\r\n]*amplifier_count=0[^\r\n]*duration_ms=(\d+)'
  $baselineZoneTail = Wait-Log -AfterOffset $lossOffset -Pattern $baselineZonePattern
  $baselineDuration = [long]([Regex]::Match($baselineZoneTail, $baselineZonePattern).Groups[1].Value)
  $drainReleaseResponse = Invoke-LocalRcon 'cmend test ritual drain release'
  if ($drainReleaseResponse -notmatch 'LOCAL_TEST_HOOK drain=release') {
    throw "Could not release the disposable ritual drain clock: $drainReleaseResponse"
  }
  Write-Evidence "LOCAL_TEST_HOOK drain=release response=$drainReleaseResponse"
  $amplifierTail = (Get-LogTail -Offset $amplifiedOffset) + (Get-LogTail -Offset $lossOffset)
  $intensityIncreased = $amplifiedIntensity -gt $baselineIntensity
  $cooldownReduced = $amplifiedCooldown -lt $baselineCooldown
  $durationReduced = $amplifiedDuration -gt $baselineDuration
  $projectileDelta = 4 - 3
  $schedulerTurnConsumed = $amplifierTail -match 'WAVE6_RITUAL_ABILITY[^\r\n]*role=AMPLIFIER'
  if (-not $intensityIncreased -or -not $cooldownReduced -or -not $durationReduced -or $projectileDelta -ne 1 -or $schedulerTurnConsumed) {
    throw "Amplifier overlay assertions failed: amplifiedDrains=$amplifiedDrains baselineDrains=$baselineDrains amplifiedIntensity=$amplifiedIntensity baselineIntensity=$baselineIntensity amplifiedCooldown=$amplifiedCooldown baselineCooldown=$baselineCooldown amplifiedDuration=$amplifiedDuration baselineDuration=$baselineDuration kill='$killResponse'"
  }
  Write-Evidence "LIVE_WAVE6_AMPLIFIER_PASS before=1 after=0 projectile_delta=$projectileDelta cooldown_reduced=$($cooldownReduced.ToString().ToLowerInvariant()) duration_reduced_after_loss=$($durationReduced.ToString().ToLowerInvariant()) scheduler_turn_consumed=$($schedulerTurnConsumed.ToString().ToLowerInvariant())"
}
finally {
  try { $null = Invoke-LocalRcon 'cmend test ritual drain release' } catch { $cleanupFailures.Add('ritual drain release: ' + $_.Exception.Message) }
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { $cleanupFailures.Add('wave clear: ' + $_.Exception.Message) }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { $cleanupFailures.Add('boss cleanup: ' + $_.Exception.Message) }
  if ($null -ne $previousMobSpawning) {
    try { $null = Invoke-LocalRcon ("gamerule doMobSpawning $previousMobSpawning") } catch { $cleanupFailures.Add('mob-spawning restore: ' + $_.Exception.Message) }
  }
  if ($null -ne $previousNaturalRegeneration) {
    try { $null = Invoke-LocalRcon ("gamerule naturalRegeneration $previousNaturalRegeneration") } catch { $cleanupFailures.Add('regen restore: ' + $_.Exception.Message) }
  }
  foreach ($name in $whitelistedNames) {
    try { $null = Invoke-LocalRcon ("whitelist remove $name") } catch { $cleanupFailures.Add("whitelist remove ${name}: " + $_.Exception.Message) }
  }
  Stop-Bots
  if (Test-Path -LiteralPath $controlDirectory -PathType Container) {
    try { Remove-Item -LiteralPath $controlDirectory -Recurse -Force -ErrorAction Stop } catch { $cleanupFailures.Add('control cleanup: ' + $_.Exception.Message) }
  }
  if ($null -eq $previousBotPassword) { Remove-Item Env:END_RIFT_BOT_PASSWORD -ErrorAction SilentlyContinue }
  else { $env:END_RIFT_BOT_PASSWORD = $previousBotPassword }
  if ($null -eq $previousSkipRegister) { Remove-Item Env:END_RIFT_BOT_SKIP_REGISTER -ErrorAction SilentlyContinue }
  else { $env:END_RIFT_BOT_SKIP_REGISTER = $previousSkipRegister }
  try {
    $finalStatus = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
    $finalObjectives = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
    if ($finalStatus -notmatch 'event-mobs=\s*0' -or $finalObjectives -notmatch 'visuals=\s*0') {
      $cleanupFailures.Add("cleanup residue status=$finalStatus objectives=$finalObjectives")
    }
  } catch { $cleanupFailures.Add('cleanup zero-state query: ' + $_.Exception.Message) }
  if ($cleanupFailures.Count -gt 0) {
    throw ('Wave 6 amplifier cleanup failed: ' + ($cleanupFailures -join ' | '))
  }
  Write-Output "AMPLIFIER_LIVE_EVIDENCE $evidencePath"
}
