[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'RiftDamageA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'RiftDamageB',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string[]]$AdditionalBotNames = @(),
  # The probe needs enough packets to observe same-tick coalescing, but must
  # stop before five disposable clients can drain the 5000-HP test boss.
  [int]$BotDurationSeconds = 35,
  [int]$TimeoutSeconds = 55,
  [switch]$RequireSameTick
)

# Local-only test.  It uses the disposable test-boss harness and real player
# attack packets, but never changes the world layout or touches production.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$playerNames = @($FirstBotName, $SecondBotName) + @($AdditionalBotNames)

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

function Get-BossSnapshot {
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  $match = [Regex]::Match($status,
    'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\s+hp=([0-9.]+)/([0-9.]+)')
  if (-not $match.Success) {
    throw "Local test boss snapshot is missing:`n$status"
  }
  $uuid = $match.Groups[1].Value
  $virtualCommand = 'data get entity ' + $uuid + ' BukkitValues."copimineendevent:end_event_boss_virtual_health"'
  $virtualData = Invoke-LocalRcon -CommandText $virtualCommand
  $virtualMatch = [Regex]::Match($virtualData, '([-0-9]+(?:\.[0-9]+)?)d\s*$')
  if (-not $virtualMatch.Success) {
    throw "Authoritative virtual HP is missing from the local boss PDC:`n$virtualData"
  }
  return [pscustomobject]@{
    Status = $status
    Uuid = $uuid
    Health = [double]::Parse($virtualMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
    MaxHealth = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
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

function Start-CombatBot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][string]$BossUuid)
  $logDirectory = Join-Path $runtimeRoot 'boss-multi-player-bots'
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
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_DELAY_MS'] = '5000'
  # 410 ms is intentionally not an integral number of server ticks.  Separate
  # real clients therefore drift through tick boundaries and reliably produce
  # an observable same-tick group without a plugin-only synthetic hit.
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_EVERY_MS'] = '410'
  $startInfo.EnvironmentVariables['END_RIFT_RAW_ATTACK'] = '1'
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
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn'
  $boss = Get-BossSnapshot
  if ($boss.MaxHealth -ne 5000.0D) {
    throw "The disposable damage harness must start at 5000 virtual HP; got $($boss.MaxHealth)."
  }
  if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
    throw "Local Paper log is missing: $paperLog"
  }
  # Freeze only the disposable test entity.  The plugin-owned local harness
  # command keeps its AI invariant from re-enabling NoAI on the next tick,
  # while the server still receives real independent attack packets and runs
  # the authoritative virtual-HP adapter.
  $null = Invoke-LocalRcon -CommandText 'cmend boss freeze'
  foreach ($name in $playerNames) {
    $processes += Start-CombatBot -Name $name -BossUuid $boss.Uuid
  }
  Wait-LocalPlayers
  # AuthMe accepts the connection before its login callback finishes.  Give
  # each disposable client a bounded grace period before issuing its combat
  # setup, otherwise a perfectly valid attack packet can be discarded while
  # the account is still in the unauthenticated join state.
  Start-Sleep -Seconds 4
  foreach ($name in $playerNames) {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("clear $name")
    $null = Invoke-LocalRcon -CommandText ("give $name minecraft:diamond_sword")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:regeneration 1000 4 true")
  }
  # Place the clients in the configured arena around the fixed test boss.
  # Their clients still send real use_entity attack packets; freezing is only
  # a deterministic property of this disposable local damage harness.
  $position = Get-BossPosition -BossUuid $boss.Uuid
  $offsets = @(@(2.4D, 0.0D), @(-2.4D, 0.0D), @(0.0D, 2.4D), @(0.0D, -2.4D), @(1.8D, 1.8D))
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $offset = $offsets[$index % $offsets.Count]
    $null = Invoke-LocalRcon -CommandText ("tp $($playerNames[$index]) " +
      (($position[0] + $offset[0]).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)) + ' ' +
      $position[1].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
      (($position[2] + $offset[1]).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)) + ' 90 0')
  }
  Start-Sleep -Seconds 2

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline -and @($processes | Where-Object { -not $_.Process.HasExited }).Count -gt 0) {
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
  $bossLines = @($log -split '\r?\n' | Where-Object { $_ -match ('BOSS_DAMAGE_EVENT .*boss=' + [Regex]::Escape($boss.Uuid) + '.*source=PLAYER:') })
  if ($bossLines.Count -lt 2) {
    throw "Fewer than two independent player damage events reached the virtual-health path:`n$($bossLines -join "`n")"
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
  if ([Math]::Abs($final.Health - $expected) -gt 0.01D) {
    throw "Virtual HP mismatch: before=$($boss.Health) final=$($final.Health) sum_final_damage=$sum expected=$expected`n$($bossLines -join "`n")"
  }
  $sameTick = @($ticks.GetEnumerator() | Where-Object { $_.Value -ge 2 }).Count
  if ($RequireSameTick -and $sameTick -lt 1) {
    throw "The five-player probe did not observe two player hits in one server tick.`n$($bossLines -join "`n")"
  }
  Write-Output "LIVE_BOSS_MULTIPLAYER_DAMAGE_PASS players=$($playerNames.Count) independent_attackers=$($attackers.Count) events=$($bossLines.Count) before=$($boss.Health) after=$($final.Health) summed_final_damage=$sum same_tick_event_groups=$sameTick boss=$($boss.Uuid)"
} finally {
  foreach ($process in $processes) {
    if ($process -and -not $process.Process.HasExited) {
      try { $process.Process.Kill() } catch { }
    }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.Process.WaitForExit(5000) | Out-Null } catch { } }
  }
  try {
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
