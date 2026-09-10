[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'RiftDamageA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'RiftDamageB',
  [string[]]$AdditionalBotNames = @(),
  # The probe needs enough packets to observe same-tick coalescing, but must
  # stop before five real clients can drain the 5000-HP local V2 boss.
  [int]$BotDurationSeconds = 35,
  [int]$TimeoutSeconds = 55,
  [switch]$RequireSameTick,
  [switch]$HighLevelAttack,
  [switch]$TraceAttackPackets
)

# Local-only test. It starts the official V2 boss and uses real independent
# player attack packets. It never changes the world layout or touches production.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$playerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames | ForEach-Object {
    [string]$_ -split ',' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  })
foreach ($playerName in $playerNames) {
  if ($playerName -notmatch '^[A-Za-z0-9_]{1,16}$') {
    throw "The multiplayer damage probe received an invalid bot name: $playerName"
  }
}
$logDirectory = Join-Path $runtimeRoot 'boss-multi-player-bots'
$attackBarrier = Join-Path $logDirectory 'release-attacks.barrier'

if ($playerNames.Count -lt 2 -or $playerNames.Count -gt 5) {
  throw "The multiplayer damage probe supports 2-5 local players; received $($playerNames.Count)."
}
if (@($playerNames | Select-Object -Unique).Count -ne $playerNames.Count) {
  throw 'The multiplayer damage probe requires unique bot names.'
}
if ((Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: the multiplayer damage probe requires environment: local.'
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local boss combat bot is missing: $botScript"
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON command failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Plain([string]$Text) {
  return ($Text -replace '\u00A7.', '')
}

function Get-BossSnapshot {
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  $match = [Regex]::Match($status,
    'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\s+hp=([0-9.]+)/([0-9.]+)')
  if (-not $match.Success) {
    throw "Local official boss snapshot is missing:`n$status"
  }
  $uuid = $match.Groups[1].Value
  $healthData = Plain (Invoke-LocalRcon -CommandText ("data get entity " + $uuid + " Health"))
  $healthMatch = [Regex]::Match($healthData, '([-0-9]+(?:\.[0-9]+)?)f\s*$')
  if (-not $healthMatch.Success) {
    throw "Real entity Health is missing from the local boss:`n$healthData"
  }
  $maxData = Plain (Invoke-LocalRcon -CommandText ("attribute " + $uuid + " minecraft:generic.max_health get"))
  $maxMatches = [Regex]::Matches($maxData, '[-0-9]+(?:\.[0-9]+)?')
  if ($maxMatches.Count -eq 0) {
    throw "Real entity max_health is missing from the local boss:`n$maxData"
  }
  return [pscustomobject]@{
    Status = $status
    Uuid = $uuid
    Health = [double]::Parse($healthMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
    MaxHealth = [double]::Parse($maxMatches[$maxMatches.Count - 1].Value, [Globalization.CultureInfo]::InvariantCulture)
  }
}

function Get-BossPosition {
  param([Parameter(Mandatory = $true)][string]$BossUuid)
  $data = Invoke-LocalRcon -CommandText ("data get entity $BossUuid Pos")
  $match = [Regex]::Match($data, '\[\s*([-0-9.]+)d,\s*([-0-9.]+)d,\s*([-0-9.]+)d\s*\]')
  if (-not $match.Success) {
    throw "The local test boss position is missing:`n$data"
  }
  return @(
    [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  )
}

function Read-SharedText {
  param([Parameter(Mandatory = $true)][string]$Path)
  $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally {
    $stream.Dispose()
  }
}

function Wait-LocalPlayers {
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Multiplayer damage bots did not join: $($playerNames -join ', ')"
}

function Wait-LocalAuthentication {
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $log = Read-SharedText -Path $paperLog
    $missing = @($playerNames | Where-Object {
        $log -notmatch ('\[AuthMe\].*' + [Regex]::Escape($_) + '\s+logged in')
      })
    if ($missing.Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Multiplayer damage bots did not complete AuthMe login: $($missing -join ', ')"
}

function Start-CombatBot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][string]$BossUuid)
  New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = (Get-Command node.exe -ErrorAction Stop).Source
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if ($null -ne $startInfo.ArgumentList) {
    $startInfo.ArgumentList.Add($botScript)
    $startInfo.ArgumentList.Add($Name)
    $startInfo.ArgumentList.Add(([string]($BotDurationSeconds * 1000)))
  } else {
    $startInfo.Arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000))
  }
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_UUID'] = $BossUuid
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_DELAY_MS'] = '750'
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_BARRIER'] = $attackBarrier
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_BARRIER_TIMEOUT_MS'] = '90000'
  # 410 ms is intentionally not an integral number of server ticks.  Separate
  # real clients therefore drift through tick boundaries and reliably produce
  # an observable same-tick group without a plugin-only synthetic hit.
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_EVERY_MS'] = '410'
  # When the caller requires an actual same-event-tick group, each independent
  # client emits a bounded raw-packet burst after the shared barrier.  The
  # normal probe keeps its sparse cadence; this mode exists only to make the
  # concurrency proof deterministic on a loaded Windows scheduler.
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_SYNC_BURST_COUNT'] = if ($RequireSameTick) { '4' } else { '0' }
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_SYNC_BURST_SPACING_MS'] = '12'
  $startInfo.EnvironmentVariables['END_RIFT_RAW_ATTACK'] = if ($HighLevelAttack) { '0' } else { '1' }
  $startInfo.EnvironmentVariables['END_RIFT_TRACE_ATTACK_PACKETS'] = if ($TraceAttackPackets) { '1' } else { '0' }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process.Start() | Out-Null
  return [pscustomobject]@{
    Process = $process
    OutputTask = $process.StandardOutput.ReadToEndAsync()
    ErrorTask = $process.StandardError.ReadToEndAsync()
  }
}

$processes = @()
$boss = $null
try {
  if (Test-Path -LiteralPath $attackBarrier -PathType Leaf) {
    Remove-Item -LiteralPath $attackBarrier -Force
  }
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn official confirm'
  # This probe measures the authoritative multi-source health transaction, not
  # pathfinding or reach. Freeze the official V2 boss only in the local
  # diagnostic environment so independent clients keep a deterministic target
  # while their packets race.
  $null = Invoke-LocalRcon -CommandText 'cmend boss freeze'
  $boss = Get-BossSnapshot
  if ($boss.MaxHealth -ne 5000.0D) {
    throw "The two-player official local probe must start at 5000 real HP; got $($boss.MaxHealth)."
  }
  if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
    throw "Local Paper log is missing: $paperLog"
  }
  foreach ($name in $playerNames) {
    $processes += Start-CombatBot -Name $name -BossUuid $boss.Uuid
  }
  Wait-LocalPlayers
  # A name in /list is not enough: AuthMe keeps pre-auth connections there.
  # Wait for the server-side login record before any inventory, teleport or
  # combat setup so every independent client is a valid participant.
  Wait-LocalAuthentication
  foreach ($name in $playerNames) {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("clear $name")
    $null = Invoke-LocalRcon -CommandText ("give $name minecraft:diamond_sword")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:regeneration 1000 4 true")
  }
  # Place the clients in the configured arena around the official boss. Their
  # clients send real use_entity attack packets while the local diagnostic
  # freeze keeps the boss target and hitbox deterministic.
  $position = Get-BossPosition -BossUuid $boss.Uuid
  $offsets = @(@(2.4D, 0.0D), @(-2.4D, 0.0D), @(0.0D, 2.4D), @(0.0D, -2.4D), @(1.8D, 1.8D))
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $offset = $offsets[$index % $offsets.Count]
    # Use the vanilla dispatcher explicitly.  Essentials' /tp alias can
    # race the Mineflayer position acknowledgement and produce a stale
    # movement packet from the pre-teleport coordinates.
    $null = Invoke-LocalRcon -CommandText ("minecraft:teleport $($playerNames[$index]) " +
      (($position[0] + $offset[0]).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)) + ' ' +
      $position[1].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
      (($position[2] + $offset[1]).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)) + ' 90 0')
  }
  Start-Sleep -Seconds 2
  foreach ($name in $playerNames) {
    $playerPosition = Plain (Invoke-LocalRcon -CommandText ("data get entity " + $name + " Pos"))
    Write-Output ("LIVE_PLAYER_POSITION name=" + $name + " " + $playerPosition)
  }
  # Release every real client only after all RCON setup and teleports have
  # completed.  This avoids measuring packets sent from the old spawn point
  # and makes a same-tick group reproducible without synthetic server hits.
  New-Item -ItemType File -Path $attackBarrier -Force | Out-Null

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $nextPositionProbe = (Get-Date).AddSeconds(4)
  while ((Get-Date) -lt $deadline -and @($processes | Where-Object { -not $_.Process.HasExited }).Count -gt 0) {
    if ((Get-Date) -ge $nextPositionProbe) {
      foreach ($name in $playerNames) {
        $combatPosition = Plain (Invoke-LocalRcon -CommandText ("data get entity " + $name + " Pos"))
        Write-Output ("LIVE_COMBAT_POSITION name=" + $name + " " + $combatPosition)
      }
      $nextPositionProbe = (Get-Date).AddSeconds(4)
    }
    Start-Sleep -Milliseconds 250
  }
  foreach ($process in $processes) {
    if (-not $process.Process.HasExited) {
      $process.Process.Kill()
      $process.Process.WaitForExit(5000)
    }
  }
  for ($index = 0; $index -lt $processes.Count; $index++) {
    $process = $processes[$index]
    $output = $process.OutputTask.GetAwaiter().GetResult()
    $errorOutput = $process.ErrorTask.GetAwaiter().GetResult()
    $botLogDirectory = Join-Path $runtimeRoot 'boss-multi-player-bots'
    [IO.File]::WriteAllText((Join-Path $botLogDirectory ($playerNames[$index] + '.log')),
      $output, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $botLogDirectory ($playerNames[$index] + '.err.log')),
      $errorOutput, [Text.UTF8Encoding]::new($false))
  }
  $log = Read-SharedText -Path $paperLog
  # The UUID is unique for this disposable boss, so filtering by UUID also
  # avoids depending on log byte offsets after Paper rotates a local log.
  $bossLines = @($log -split '\r?\n' | Where-Object { $_ -match ('BOSS_V2_DAMAGE_ACCEPTED .*boss=' + [Regex]::Escape($boss.Uuid) + '.*source=PLAYER:') })
  if ($bossLines.Count -lt 2) {
    throw "Fewer than two independent player damage events reached the official real-health path:`n$($bossLines -join "`n")"
  }
  $notCommitted = @($bossLines | Where-Object { $_ -notmatch 'accepted=true cancelled=true authority=entity-health' })
  if ($notCommitted.Count -gt 0) {
    throw "An accepted V2 boss hit was not marked as a committed real-health transaction:`n$($notCommitted -join "`n")"
  }
  $damagePattern = 'source=PLAYER:([0-9a-fA-F-]+).*?final=([0-9]+(?:\.[0-9]+)?).*?tick=([0-9]+)'
  $sum = 0.0D
  $attackers = [Collections.Generic.HashSet[string]]::new()
  $ticks = @{}
  foreach ($line in $bossLines) {
    $match = [Regex]::Match($line, $damagePattern)
    if (-not $match.Success) { continue }
    [void]$attackers.Add($match.Groups[1].Value)
    $damage = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
    $sum += $damage
    $tick = $match.Groups[3].Value
    if (-not $ticks.ContainsKey($tick)) { $ticks[$tick] = 0 }
    $ticks[$tick]++
  }
  if ($attackers.Count -lt 2) {
    throw "Damage events came from fewer than two player UUIDs: $($attackers -join ',')"
  }
  $final = Get-BossSnapshot
  $expected = [Math]::Max(0.0D, $boss.Health - $sum)
  $healthDelta = [Math]::Abs($final.Health - $expected)
  # Bukkit stores LivingEntity health as a float.  Across a five-client burst,
  # each accepted transaction is rounded when it is written back, so the
  # authoritative log sum can differ from /data by a few hundredths without
  # representing lost damage.  Keep the bound tight enough to catch a missed
  # hit while allowing the documented float quantisation.
  if ($healthDelta -gt 0.05D) {
    throw "Real entity HP mismatch: before=$($boss.Health) final=$($final.Health) sum_final_damage=$sum expected=$expected delta=$healthDelta`n$($bossLines -join "`n")"
  }
  $sameTick = @($ticks.GetEnumerator() | Where-Object { $_.Value -ge 2 }).Count
  if ($RequireSameTick -and $sameTick -lt 1) {
    throw "The $($playerNames.Count)-player probe did not observe two player hits in one server tick.`n$($bossLines -join "`n")"
  }
  Write-Output "LIVE_BOSS_MULTIPLAYER_REAL_HEALTH_PASS players=$($playerNames.Count) independent_attackers=$($attackers.Count) events=$($bossLines.Count) before=$($boss.Health) after=$($final.Health) summed_final_damage=$sum expected=$expected health_delta=$healthDelta same_tick_event_groups=$sameTick boss=$($boss.Uuid)"
} finally {
  if (Test-Path -LiteralPath $attackBarrier -PathType Leaf) {
    Remove-Item -LiteralPath $attackBarrier -Force
  }
  foreach ($process in $processes) {
    if ($process -and -not $process.Process.HasExited) {
      try { $process.Process.Kill() } catch { }
    }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.Process.WaitForExit(5000) | Out-Null } catch { } }
  }
  try {
    $null = Invoke-LocalRcon -CommandText 'cmend boss unfreeze'
    $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
    $cleanup = Invoke-LocalRcon -CommandText 'cmend status'
    if ($cleanup -notmatch 'boss=.*none') {
      throw "Multiplayer damage probe cleanup left a boss: $cleanup"
    }
    Write-Output 'LIVE_BOSS_MULTIPLAYER_DAMAGE_CLEANUP_PASS boss=none'
  } catch {
    Write-Warning "Local multiplayer damage cleanup failed: $($_.Exception.Message)"
  }
}
