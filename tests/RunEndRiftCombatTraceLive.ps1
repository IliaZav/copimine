[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'RiftTraceBot',
  [ValidateRange(40, 90)]
  [int]$BotDurationSeconds = 60
)

# Local-only diagnostic probe. It starts no production service, preserves the
# existing local world, and leaves no wave/boss entities after it completes.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtime = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtime 'end-rift-server')).Path
$rcon = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$waveBotScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$bossBotScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'

function Rcon([string]$command) {
  $result = & powershell -NoProfile -ExecutionPolicy Bypass -File $rcon -ServerDir $serverDir -RconPort 25576 -CommandText $command | Out-String
  if ($LASTEXITCODE -ne 0) { throw "RCON failed: $command`n$result" }
  $result.Trim()
}

function Core([string]$status) {
  $match = [Regex]::Match($status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core coordinates missing:`n$status" }
  @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function BossUuid([string]$status) {
  $match = [Regex]::Match($status, 'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) { throw "Boss UUID missing:`n$status" }
  $match.Groups[1].Value
}

function BossPosition([string]$uuid) {
  $data = Rcon "data get entity $uuid Pos"
  $match = [Regex]::Match($data, '\[\s*([-0-9.]+)d,\s*([-0-9.]+)d,\s*([-0-9.]+)d\s*\]')
  if (-not $match.Success) { throw "Boss position missing:`n$data" }
  @(
    [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  )
}

function Coordinate([double]$value) {
  $value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function New-BotProcess([string]$name, [int]$durationSeconds, [string]$scriptPath, [hashtable]$environment = @{}) {
  $info = [Diagnostics.ProcessStartInfo]::new()
  $info.FileName = (Get-Command node.exe -ErrorAction Stop).Source
  $info.WorkingDirectory = $root
  $info.UseShellExecute = $false
  $info.CreateNoWindow = $true
  $info.RedirectStandardOutput = $true
  $info.RedirectStandardError = $true
  if ($null -ne $info.ArgumentList) {
    $info.ArgumentList.Add($scriptPath)
    $info.ArgumentList.Add($name)
    $info.ArgumentList.Add(([string]($durationSeconds * 1000)))
  } else {
    $info.Arguments = '"' + $scriptPath + '" ' + $name + ' ' + ([string]($durationSeconds * 1000))
  }
  foreach ($entry in $environment.GetEnumerator()) {
    $info.EnvironmentVariables[[string]$entry.Key] = [string]$entry.Value
  }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $info
  $process.Start() | Out-Null
  [pscustomobject]@{
    Process = $process
    Out = $process.StandardOutput.ReadToEndAsync()
    Err = $process.StandardError.ReadToEndAsync()
  }
}

function Wait-Player([string]$name) {
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    if ((Rcon 'list') -match [Regex]::Escape($name)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Trace bot did not join: $name"
}

function New-TraceWindow() {
  return @((Get-Content -LiteralPath $paperLog -ErrorAction Stop)).Count
}

function Read-NewTraceLines([int]$startLine) {
  @((Get-Content -LiteralPath $paperLog -ErrorAction Stop) | Select-Object -Skip $startLine |
    Where-Object { $_ -match 'COMBAT_TRACE ' })
}

function Get-TraceDamage([string]$line) {
  $match = [Regex]::Match($line,
    'final=([0-9]+(?:\.[0-9]+)?).*health_before=([0-9]+(?:\.[0-9]+)?).*health_next_tick=([0-9]+(?:\.[0-9]+)?)')
  if (-not $match.Success) { return $null }
  [pscustomobject]@{
    Final = [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
    Before = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
    After = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  }
}

$waveBot = $null
$bossBot = $null
$traceSucceeded = $false
try {
  if ((Get-Content (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'Refused: End Rift source config is not local.'
  }
  if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
    throw "Local Paper log is missing: $paperLog"
  }
  $null = Rcon 'cmend wave clear'
  $null = Rcon 'cmend boss kill cleanup'
  # Combat Trace is deliberately opt-in in the plugin.  Enable it before any
  # probe damage so the live test observes the complete Bukkit transaction,
  # then disable it in finally to avoid leaking diagnostic logging into the
  # next local scenario.
  $null = Rcon 'cmend debug trace on'
  $core = Core (Rcon 'cmend status')
  $waveBot = New-BotProcess $BotName $BotDurationSeconds $waveBotScript
  Wait-Player $BotName
  $null = Rcon "gamemode survival $BotName"
  $null = Rcon "clear $BotName"
  $null = Rcon "give $BotName minecraft:netherite_sword 1"
  $null = Rcon "attribute $BotName minecraft:generic.max_health base set 1000"
  $null = Rcon "heal $BotName"
  $null = Rcon "effect give $BotName minecraft:resistance 1000 4 true"
  $null = Rcon "tp $BotName $($core[0] + 8) $($core[1] + 1) $($core[2]) 90 0"
  Start-Sleep -Seconds 2

  $waveStart = New-TraceWindow
  $waveResult = Rcon 'cmend test wave 1'
  if ($waveResult -match '(?i)refused|not created|event world') { throw "Wave trace setup refused: $waveResult" }
  Start-Sleep -Seconds ([Math]::Max(20, $BotDurationSeconds - 34))
  $waveTraces = Read-NewTraceLines $waveStart
  $playerWave = @($waveTraces | Where-Object { $_ -match 'attacker_kind=PLAYER' })
  $exactWave = @($playerWave | Where-Object {
    $damage = Get-TraceDamage $_
    $null -ne $damage -and [Math]::Abs(($damage.Before - $damage.After) - $damage.Final) -le 0.02D
  })
  if ($playerWave.Count -lt 5 -or $exactWave.Count -lt 5) {
    throw "Player-to-wave-mob trace did not retain every computed final damage: player=$($playerWave.Count) exact=$($exactWave.Count)`n$($playerWave -join "`n")"
  }
  if (-not $waveBot.Process.HasExited) { $waveBot.Process.Kill() }
  $waveBot.Process.WaitForExit(5000) | Out-Null
  $waveBotOutput = $waveBot.Out.GetAwaiter().GetResult()
  $waveBotError = $waveBot.Err.GetAwaiter().GetResult()
  if ($waveBotOutput -notmatch 'PLAYER_ATTACK') {
    throw "Wave trace bot never sent a real attack packet:`n$waveBotOutput`n$waveBotError"
  }

  $null = Rcon 'cmend wave clear'
  # Clearing a wave deliberately tells connected clients to stop its local
  # combat loop.  Use a fresh bot for the boss half of this diagnostic so the
  # test exercises a real player packet rather than reactivating a client.
  $bossBotName = if ($BotName.Length -le 12) { $BotName + 'Boss' } else { 'RiftTraceB' }
  $bossResult = Rcon 'cmend boss spawn official confirm'
  if ($bossResult -match '(?i)refused|not created|event world') { throw "Boss trace setup refused: $bossResult" }
  $bossUuid = BossUuid (Rcon 'cmend status')
  $bossStart = New-TraceWindow
  $bossBot = New-BotProcess $bossBotName $BotDurationSeconds $bossBotScript @{
    END_RIFT_BOSS_UUID = $bossUuid
    END_RIFT_BOSS_ATTACK_DELAY_MS = '7000'
    END_RIFT_BOSS_ATTACK_EVERY_MS = '900'
  }
  Wait-Player $bossBotName
  # AuthMe finishes its first accepted login after the network join event.
  # Configuring coordinates earlier lets that login restore the spawn point
  # over the RCON teleport, yielding a harmless but non-combat-capable probe.
  Start-Sleep -Seconds 7
  $null = Rcon "gamemode survival $bossBotName"
  $null = Rcon "clear $bossBotName"
  $null = Rcon "give $bossBotName minecraft:netherite_sword 1"
  $null = Rcon "attribute $bossBotName minecraft:generic.max_health base set 1000"
  $null = Rcon "heal $bossBotName"
  $null = Rcon "effect give $bossBotName minecraft:resistance 1000 4 true"
  $null = Rcon "effect give $bossBotName minecraft:regeneration 1000 4 true"
  $null = Rcon "give $bossBotName minecraft:diamond_sword 1"
  # The boss moves during its normal AI loop. Seed the player beside its current
  # server entity; the dedicated packet client continues following its UUID.
  $position = BossPosition $bossUuid
  $null = Rcon ("tp $bossBotName $(Coordinate ($position[0] + 1.8D)) $(Coordinate $position[1]) $(Coordinate $position[2]) 90 0")
  $null = Rcon "gamemode survival $bossBotName"
  Start-Sleep -Seconds 18
  $bossTraces = Read-NewTraceLines $bossStart
  $bossTrace = @($bossTraces | Where-Object {
    $_ -match ('victim=' + [Regex]::Escape($bossUuid)) -and $_ -match 'attacker_kind=PLAYER'
  })
  if ($bossTrace.Count -lt 1) {
    throw "No player-to-boss Combat Trace was emitted:`n$($bossTraces -join "`n")"
  }
  if (-not $bossBot.Process.HasExited) { $bossBot.Process.Kill() }
  $bossBot.Process.WaitForExit(5000) | Out-Null
  $bossBotOutput = $bossBot.Out.GetAwaiter().GetResult()
  $bossBotError = $bossBot.Err.GetAwaiter().GetResult()
  if ($bossBotOutput -notmatch 'PLAYER_ATTACK') {
    throw "Boss trace bot never sent a real attack packet:`n$bossBotOutput`n$bossBotError"
  }
  $traceSucceeded = $true
  Write-Output "LIVE_COMBAT_TRACE_PASS wave_traces=$($waveTraces.Count) player_wave=$($playerWave.Count) exact_wave=$($exactWave.Count) boss_traces=$($bossTrace.Count) wave_bot=$BotName boss_bot=$bossBotName"
} finally {
  try { Rcon 'cmend wave clear' | Out-Null } catch { }
  try { Rcon 'cmend boss kill cleanup' | Out-Null } catch { }
  try { Rcon 'cmend debug trace off' | Out-Null } catch { }
  foreach ($bot in @($waveBot, $bossBot)) {
    if ($bot -and -not $bot.Process.HasExited) { try { $bot.Process.Kill() } catch { } }
    if ($bot) { try { $bot.Process.WaitForExit(5000) | Out-Null } catch { } }
    if ($bot) {
      try {
        if (-not $traceSucceeded) {
          $diagnostic = $bot.Out.GetAwaiter().GetResult()
          if ($diagnostic) { Write-Output "COMBAT_TRACE_BOT_DIAGNOSTIC`n$diagnostic" }
        }
      } catch { }
    }
  }
}
