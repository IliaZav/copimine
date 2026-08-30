[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftSetupBot',
  [int]$BotDurationSeconds = 42,
  [ValidateSet('creative', 'survival')]
  [string]$AttackGameMode = 'survival'
)

# This probe is local-only and must never be pointed at production.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$botLog = Join-Path $runtimeRoot 'boss-damage-live-bot.log'
$botErr = Join-Path $runtimeRoot 'boss-damage-live-bot.err.log'

if ((Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: End Rift live probe requires environment: local.'
}
if (-not (Test-Path -LiteralPath $serverDir -PathType Container)) {
  throw "Local Paper directory is missing: $serverDir"
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local boss combat probe is missing: $botScript"
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

function Get-Status {
  return Invoke-LocalRcon -CommandText 'cmend status'
}

function Get-BossUuid([string]$Status) {
  $match = [Regex]::Match($Status, 'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) { throw "Live boss is missing from local status:`n$Status" }
  return $match.Groups[1].Value
}

function Get-BossPosition([string]$Uuid) {
  $data = Invoke-LocalRcon -CommandText ("data get entity $Uuid Pos")
  $match = [Regex]::Match($data, '\[\s*([-0-9.]+)d,\s*([-0-9.]+)d,\s*([-0-9.]+)d\s*\]')
  if (-not $match.Success) { throw "Unable to parse local boss position:`n$data" }
  return @(
    [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  )
}

function Format-Coordinate([double]$Value) {
  return $Value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Wait-LocalBot {
  for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if ($list -match [Regex]::Escape($BotName)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Local combat probe player did not join in time: $BotName"
}

$bossUuid = $null
$process = $null
try {
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn official confirm'
  $status = Get-Status
  $bossUuid = Get-BossUuid $status

  # Start at 5000 virtual HP and cross the real 2000 HP Absorption threshold.  The
  # five-second cast is allowed to finish before the player probe attacks.
  $null = Invoke-LocalRcon -CommandText 'cmend boss damage 3000'
  Start-Sleep -Seconds 7
  $afterAbsorption = Get-Status
  if ($afterAbsorption -notmatch 'hp=.*?2000/5000') {
    throw "Local boss did not reach the post-Absorption 2000 HP checkpoint:`n$afterAbsorption"
  }

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $node
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if ($null -ne $startInfo.ArgumentList) {
    $startInfo.ArgumentList.Add($botScript)
    $startInfo.ArgumentList.Add($BotName)
    $startInfo.ArgumentList.Add(([string]($BotDurationSeconds * 1000)))
  } else {
    # Windows PowerShell 5.1/.NET Framework has no ArgumentList property.
    # Keep the path quoted because a checkout may live under a spaced folder.
    $startInfo.Arguments = '"' + $botScript + '" ' + $BotName + ' ' + ([string]($BotDurationSeconds * 1000))
  }
  # Windows PowerShell exposes the mutable environment as EnvironmentVariables;
  # using Environment here returns null on the local runner and aborts the
  # probe before the packet bot can even connect.
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_UUID'] = $bossUuid
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_DELAY_MS'] = '7000'
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_EVERY_MS'] = '900'
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process.Start() | Out-Null
  $stdoutTask = $process.StandardOutput.ReadToEndAsync()
  $stderrTask = $process.StandardError.ReadToEndAsync()

  Wait-LocalBot
  # Configure a durable survival player.  The boss keeps its normal AI during
  # this probe; the bot's target UUID filter prevents a wave mob from being
  # mistaken for the official boss.
  $null = Invoke-LocalRcon -CommandText ("attribute $BotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon -CommandText ("heal $BotName")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("give $BotName minecraft:diamond_sword")
  # Read the boss position only after the packet client has joined and the
  # setup commands have completed.  The boss can move during that window;
  # using the pre-join position makes every attack packet miss the hitbox.
  $position = Get-BossPosition $bossUuid
  # Keep the probe outside the boss hitbox and make vanilla attack validation
  # deterministic: the player stands 1.8 blocks east of the boss and faces
  # west. A zero-distance teleport can be rejected even when the packet is
  # otherwise a valid serverbound attack packet.
  $attackX = $position[0] + 1.8D
  $attackY = $position[1]
  $attackZ = $position[2]
  $null = Invoke-LocalRcon -CommandText ("tp $BotName $(Format-Coordinate $attackX) $(Format-Coordinate $attackY) $(Format-Coordinate $attackZ) 90 0")
  if ($AttackGameMode -eq 'survival') {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $BotName")
  }

  $timeoutAt = (Get-Date).AddSeconds($BotDurationSeconds + 8)
  while (-not $process.HasExited -and (Get-Date) -lt $timeoutAt) {
    Start-Sleep -Milliseconds 500
  }
  if (-not $process.HasExited) {
    $process.Kill()
    $process.WaitForExit(5000)
    throw 'Local boss combat probe timed out.'
  }
  $stdout = $stdoutTask.GetAwaiter().GetResult()
  $stderr = $stderrTask.GetAwaiter().GetResult()
  [IO.File]::WriteAllText($botLog, $stdout, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText($botErr, $stderr, [Text.UTF8Encoding]::new($false))
  if ($process.ExitCode -ne 0 -or $stdout -notmatch 'PLAYER_END .*attacks=[1-9]') {
    throw "Local boss combat probe failed with exit code $($process.ExitCode).`n$stdout`n$stderr"
  }

  $finalStatus = Get-Status
  $finalMatch = [Regex]::Match($finalStatus, 'hp=.*?([0-9.]+)/5000')
  if (-not $finalMatch.Success) { throw "Local boss final HP is missing:`n$finalStatus" }
  $finalHealth = [double]::Parse($finalMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
  if ($finalHealth -ge 2000.0D) {
    throw "Real survival player attacks did not reduce the post-Absorption boss HP:`n$finalStatus`n$stdout"
  }
  $damageLines = Select-String -LiteralPath (Join-Path $serverDir 'logs\latest.log') `
    -Pattern ("BOSS_DAMAGE_EVENT .*boss=" + [Regex]::Escape($bossUuid) + ".*source=PLAYER:")
  if (-not $damageLines) {
    throw "The server did not observe a player-sourced boss damage event for $bossUuid.`n$stdout"
  }
  Write-Output "LIVE_BOSS_DAMAGE_PASS before=1000 after=$finalHealth boss=$bossUuid bot=$BotName player_damage_events=$($damageLines.Count)"
  Write-Output $afterAbsorption
  Write-Output $finalStatus
} finally {
  if ($process -and -not $process.HasExited) {
    try { $process.Kill() } catch { }
  }
  try {
    Invoke-LocalRcon -CommandText 'cmend boss kill cleanup' | Out-Null
    $cleanupStatus = Get-Status
    if ($cleanupStatus -notmatch 'boss=.*none' -or $cleanupStatus -notmatch 'event-mobs=.*0') {
      throw "Local boss cleanup left event entities behind:`n$cleanupStatus"
    }
    Write-Output 'LIVE_BOSS_DAMAGE_CLEANUP_PASS event-mobs=0 boss=none'

    # The official-boss harness intentionally enters BOSS_ACTIVE.  Restore
    # only the configured local scene so a later restart never rehydrates a
    # deliberately removed harness boss as RECOVERY_REQUIRED.  This is not a
    # world/player-data reset and is bounded to the isolated End Rift Core.
    $null = Invoke-LocalRcon -CommandText 'cmend core remove confirm'
    $null = Invoke-LocalRcon -CommandText 'cmend core setat 8 68 -39 2'
    $null = Invoke-LocalRcon -CommandText 'cmend resources reset confirm'
    foreach ($resource in @(
      'DIAMOND 100', 'ENDER_EYE 64', 'AMETHYST_SHARD 128', 'BLAZE_ROD 64'
    )) {
      $null = Invoke-LocalRcon -CommandText ("cmend resources add $resource")
    }
    $restoredStatus = Get-Status
    if (($restoredStatus -notmatch 'state=.*READY_FOR_PLAYERS') -or
        ($restoredStatus -notmatch 'boss=.*none') -or
        ($restoredStatus -notmatch 'event-mobs=.*0')) {
      throw "Local boss harness scene restore failed:`n$restoredStatus"
    }
    Write-Output 'LIVE_BOSS_DAMAGE_SCENE_RESTORE_PASS state=READY_FOR_PLAYERS boss=none event-mobs=0'
  } catch {
    Write-Error $_
  }
}
