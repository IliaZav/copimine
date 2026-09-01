[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'ObeliskProbeA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'ObeliskProbeB',
  [int]$BotDurationSeconds = 80,
  [int]$TimeoutSeconds = 95
)

# Local-only integration probe. It uses real Mineflayer player connections,
# real use_entity packets and the checked-out local Paper/PDC state. It never
# rebuilds the world scene and refuses a non-local End Rift configuration.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$obeliskBotScript = Join-Path $root 'tests\LocalEndRiftObeliskBot.js'
$combatBotScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLogDirectory = Join-Path $runtimeRoot 'rift-obelisk-bots'
$playerNames = @($FirstBotName, $SecondBotName)
$processes = @()

if (@($playerNames | Select-Object -Unique).Count -ne $playerNames.Count) {
  throw 'The obelisk probe requires unique player names.'
}
if ((Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: the Rift Obelisk probe requires environment: local.'
}
foreach ($path in @($obeliskBotScript, $combatBotScript, $paperLog)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Required local probe file is missing: $path"
  }
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

function Get-LogLength {
  # Wait-LogCount slices the decoded UTF-8 string, so the checkpoint must be
  # measured in characters rather than raw file bytes. Cyrillic plugin
  # messages make those offsets diverge.
  return (Read-SharedText -Path $paperLog).Length
}

function Wait-LogCount {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int]$Minimum,
    [Parameter(Mandatory = $true)][long]$AfterOffset,
    [int]$WaitSeconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $text = Read-SharedText -Path $paperLog
    $tail = ''
    if ($AfterOffset -lt $text.Length) {
      $tail = $text.Substring([int]$AfterOffset)
    }
    $count = ([Regex]::Matches($tail, $Pattern)).Count
    if ($count -ge $Minimum) {
      return $tail
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for $Minimum log matches of '$Pattern'. See $paperLog"
}

function Wait-LocalPlayers {
  param([Parameter(Mandatory = $true)][string[]]$Names)
  for ($attempt = 0; $attempt -lt 80; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if (@($Names | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Local probe players did not join: $($Names -join ', ')"
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
    throw "Authoritative boss virtual HP is missing:`n$virtualData"
  }
  $physicalMatch = [Regex]::Match($status, 'physical=([0-9.]+)/([0-9.]+)')
  $physical = -1.0D
  if ($physicalMatch.Success) {
    $physical = [double]::Parse($physicalMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
  }
  return [pscustomobject]@{
    Status = $status
    Uuid = $uuid
    Health = [double]::Parse($virtualMatch.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
    MaxHealth = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
    Physical = $physical
  }
}

function Get-BossPosition {
  param([Parameter(Mandatory = $true)][string]$BossUuid)
  $data = Invoke-LocalRcon -CommandText ("data get entity $BossUuid Pos")
  $match = [Regex]::Match($data, '\[\s*([-0-9.]+)d,\s*([-0-9.]+)d,\s*([-0-9.]+)d\s*\]')
  if (-not $match.Success) { throw "Local boss position is missing:`n$data" }
  return @(
    [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  )
}

function Assert-ArenaFloorStone {
  param(
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y,
    [Parameter(Mandatory = $true)][int]$Z,
    [Parameter(Mandatory = $true)][string]$Marker
  )
  $offset = Get-LogLength
  # `data get block` only works for block entities (chests, command blocks,
  # etc.), not an ordinary arena floor block. Use the vanilla block predicate
  # against the known local arena material and require its observable log
  # marker instead.
  $null = Invoke-LocalRcon -CommandText (
    "execute if block $X $Y $Z minecraft:stone run minecraft:say $Marker")
  Wait-LogCount -Pattern ([Regex]::Escape($Marker)) -Minimum 1 -AfterOffset $offset -WaitSeconds 5 | Out-Null
}

function Start-NodeProbe {
  param(
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [Parameter(Mandatory = $true)][string[]]$Arguments,
    [Parameter(Mandatory = $true)][hashtable]$Environment
  )
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = (Get-Command node.exe -ErrorAction Stop).Source
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if ($null -ne $startInfo.ArgumentList) {
    $startInfo.ArgumentList.Add($ScriptPath)
    foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
  } else {
    $quoted = @('"' + $ScriptPath + '"') + @($Arguments | ForEach-Object { '"' + $_ + '"' })
    $startInfo.Arguments = $quoted -join ' '
  }
  foreach ($entry in $Environment.GetEnumerator()) {
    $startInfo.EnvironmentVariables[$entry.Key] = [string]$entry.Value
  }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process.Start() | Out-Null
  return [pscustomobject]@{
    Process = $process
    OutputTask = $process.StandardOutput.ReadToEndAsync()
    ErrorTask = $process.StandardError.ReadToEndAsync()
  }
}

function Save-ProbeOutput {
  param([Parameter(Mandatory = $true)][object]$Probe, [Parameter(Mandatory = $true)][string]$Name)
  $output = $Probe.OutputTask.GetAwaiter().GetResult()
  $errorOutput = $Probe.ErrorTask.GetAwaiter().GetResult()
  [IO.File]::WriteAllText((Join-Path $botLogDirectory ($Name + '.log')), $output,
    [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText((Join-Path $botLogDirectory ($Name + '.err.log')), $errorOutput,
    [Text.UTF8Encoding]::new($false))
  return [pscustomobject]@{ Output = $output; Error = $errorOutput }
}

function Wait-PlayerEffects {
  param(
    [Parameter(Mandatory = $true)][string]$ImpactLog,
    [Parameter(Mandatory = $true)][long]$AfterOffset,
    [int]$WaitSeconds = 12
  )
  $affectedMatch = [Regex]::Match($ImpactLog,
    'affected=\[([^\]]*)\]')
  if (-not $affectedMatch.Success) {
    throw "Rift Fireball impact did not report its affected players:`n$ImpactLog"
  }
  $playerIds = @([Regex]::Matches($affectedMatch.Groups[1].Value,
    '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}') |
    ForEach-Object { $_.Value })
  if ($playerIds.Count -eq 0) {
    throw "Rift Fireball impact did not apply effects to a player:`n$ImpactLog"
  }
  $pattern = 'RIFT_FIREBALL_EFFECTS_APPLIED .*player={0} .*damage=6\.0 ' +
    'blindness_ticks=40 blindness_amplifier=0 ' +
    'weakness_ticks=60 weakness_amplifier=0 ' +
    'nausea_ticks=60 nausea_amplifier=1 ' +
    'slowness_ticks=60 slowness_amplifier=0'
  foreach ($playerId in $playerIds) {
    Wait-LogCount -Pattern ($pattern -f [Regex]::Escape($playerId)) -Minimum 1 `
      -AfterOffset $AfterOffset -WaitSeconds $WaitSeconds | Out-Null
  }
  return ($playerIds -join ',')
}

$boss = $null
$obeliskBots = @()
$normalBot = $null
try {
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn'
  $boss = Get-BossSnapshot
  if ($boss.MaxHealth -ne 5000.0D) {
    throw "The disposable obelisk harness must start at 5000 virtual HP; got $($boss.MaxHealth)."
  }
  $null = Invoke-LocalRcon -CommandText ("data merge entity $($boss.Uuid) {NoAI:1b}")
  $status = $boss.Status
  $coreMatch = [Regex]::Match($status, 'core=.*?(-?\d+),(-?\d+),(-?\d+)')
  if (-not $coreMatch.Success) { throw "Local Core coordinates are missing:`n$status" }
  $coreX = [double]$coreMatch.Groups[1].Value
  $coreY = [double]$coreMatch.Groups[2].Value
  $coreZ = [double]$coreMatch.Groups[3].Value
  $obeliskPosition = @(($coreX + 7.5D), $coreY, ($coreZ + 0.5D))
  $floorX = [int]$coreX + 7
  $floorY = [int]$coreY - 1
  $floorZ = [int]$coreZ
  Assert-ArenaFloorStone -X $floorX -Y $floorY -Z $floorZ -Marker 'RIFT_OBELISK_BLOCK_STONE_BEFORE'

  $commonEnvironment = @{
    END_RIFT_BOT_HOST = '127.0.0.1'
    END_RIFT_BOT_PORT = '25566'
    # Let the first projectile land without a reflection so the exact
    # fireball damage/effect contract is observed before the reflection loop.
    # The first two observed event projectiles are deliberately left alone;
    # reflection then starts from the third one.  This is independent of
    # login, teleport and server startup timing, so the direct-impact and
    # reflected-impact contracts cannot race each other.
    END_RIFT_REFLECT_START_MS = '0'
    END_RIFT_REFLECT_AFTER_FIREBALLS = '2'
    END_RIFT_OBELISK_X = $obeliskPosition[0].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
    END_RIFT_OBELISK_Y = $obeliskPosition[1].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
    END_RIFT_OBELISK_Z = $obeliskPosition[2].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
  }
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $name = $playerNames[$index]
    # One client owns the reflection attempts; the second remains a real
    # participant/observer so duplicate use_entity packets cannot race each
    # other and make the live assertion flaky.
    $commonEnvironment['END_RIFT_REFLECT_ENABLED'] = if ($index -eq 0) { '1' } else { '0' }
    $obeliskBots += Start-NodeProbe -ScriptPath $obeliskBotScript `
      -Arguments @($name, ([string]($BotDurationSeconds * 1000))) -Environment $commonEnvironment
  }
  Wait-LocalPlayers -Names $playerNames
  Start-Sleep -Seconds 4
  foreach ($name in $playerNames) {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("clear $name")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.max_health base set 1000")
    # Keep the reflector on the prepared vantage point while the disposable
    # boss is still running its own spell loop; otherwise a knockback can put
    # the reflected trajectory through the protected Core.
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.knockback_resistance base set 1")
    $null = Invoke-LocalRcon -CommandText ("effect clear $name")
    # Keep the disposable probe alive and stationary while still recording
    # the fireball's configured damage/effects in the server log.  This
    # removes unrelated boss/wave knockback from the reflection assertion.
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 255 60 true")
    # Keep the effect NBT list limited to the four fireball effects.  The
    # large health pool makes the probe safe without adding unrelated effects
    # that would make Paper abbreviate active_effects with an ellipsis.
    $null = Invoke-LocalRcon -CommandText ("data merge entity $name {Health:1000f}")
  }
  # Keep the reflector between the obelisk and the second participant.  This
  # gives the client enough travel time to receive and swing at a LargeFireball
  # even when the fair-target cursor selects either participant.  The return
  # path still points away from the protected Core.
  $positions = @(
    @(($coreX + 4.0D), $coreY, ($coreZ - 0.5D)),
    @(($coreX + 1.0D), $coreY, ($coreZ - 0.5D))
  )
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $position = $positions[$index]
    $null = Invoke-LocalRcon -CommandText ("tp $($playerNames[$index]) " +
      $position[0].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
      $position[1].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
      $position[2].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 90 0')
  }
  Start-Sleep -Seconds 2

  # 5000 -> 2500 enters DISTORTION and unlocks the requested spell without
  # entering the scripted Absorption/Judgment states.
  $null = Invoke-LocalRcon -CommandText 'cmend boss damage 2500'
  $null = Invoke-LocalRcon -CommandText 'cmend boss phase normal'
  $preHazard = Get-BossSnapshot
  if ($preHazard.Status -notmatch 'half=.*true' -or $preHazard.Status -notmatch 'boss=.*hp=2500/5000') {
    throw "Local boss did not reach the DISTORTION checkpoint:`n$($preHazard.Status)"
  }
  $spellOffset = Get-LogLength
  $null = Invoke-LocalRcon -CommandText 'cmend boss spell rift_obelisks'
  Wait-LogCount -Pattern 'RIFT_OBELISKS_SPAWNED .*count=1' -Minimum 1 -AfterOffset $spellOffset | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_ACTIVE ' -Minimum 1 -AfterOffset $spellOffset | Out-Null
  Wait-LogCount -Pattern 'RIFT_FIREBALL_LAUNCH .*reflected=false' -Minimum 1 -AfterOffset $spellOffset | Out-Null
  $impactLog = Wait-LogCount -Pattern 'RIFT_FIREBALL_IMPACT .*reflected=false .*damage=6\.0 .*blindness_ticks=40 .*debuff_ticks=60 .*blocks=false fire=false' `
    -Minimum 1 -AfterOffset $spellOffset
  $effectPlayer = Wait-PlayerEffects -ImpactLog $impactLog -AfterOffset $spellOffset
  Wait-LogCount -Pattern 'RIFT_FIREBALL_REFLECTED ' -Minimum 3 -AfterOffset $spellOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_REFLECTED_HIT .*remaining_health=2 destroyed=false' -Minimum 1 -AfterOffset $spellOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_REFLECTED_HIT .*remaining_health=1 destroyed=false' -Minimum 1 -AfterOffset $spellOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_REFLECTED_HIT .*remaining_health=0 destroyed=true' -Minimum 1 -AfterOffset $spellOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_CLEANUP .*reason=destroyed' -Minimum 1 -AfterOffset $spellOffset -WaitSeconds $BotDurationSeconds | Out-Null
  $hazardAfter = Get-BossSnapshot
  if ([Math]::Abs($hazardAfter.Health - $preHazard.Health) -gt 0.0001D) {
    throw "Rift Fireball changed boss virtual HP: before=$($preHazard.Health) after=$($hazardAfter.Health)"
  }
  $physicalChanged = $hazardAfter.Physical -ge 0.0D -and $preHazard.Physical -ge 0.0D -and
    [Math]::Abs($hazardAfter.Physical - $preHazard.Physical) -gt 0.01D
  if ($physicalChanged) {
    throw "Rift Fireball changed boss physical HP: before=$($preHazard.Physical) after=$($hazardAfter.Physical)"
  }
  Assert-ArenaFloorStone -X $floorX -Y $floorY -Z $floorZ -Marker 'RIFT_OBELISK_BLOCK_STONE_AFTER'
  Write-Output "LIVE_RIFT_OBELISK_HAZARD_PASS boss=$($boss.Uuid) obelisks=1 fireballs=event-owned effects_player=$effectPlayer boss_virtual_before=$($preHazard.Health) boss_virtual_after=$($hazardAfter.Health) physical_before=$($preHazard.Physical) physical_after=$($hazardAfter.Physical) arena_block=minecraft:stone"

  # After the reflected-only mechanic is gone, use a separate real survival
  # client to prove ordinary player damage still reaches the boss path.
  $normalBefore = Get-BossSnapshot
  $normalEnvironment = @{
    END_RIFT_BOT_HOST = '127.0.0.1'
    END_RIFT_BOT_PORT = '25566'
    END_RIFT_BOSS_UUID = $boss.Uuid
    END_RIFT_BOSS_ATTACK_DELAY_MS = '2500'
    END_RIFT_BOSS_ATTACK_EVERY_MS = '900'
    END_RIFT_RAW_ATTACK = '1'
  }
  $normalBot = Start-NodeProbe -ScriptPath $combatBotScript `
    -Arguments @('ObeliskNormalHit', '12000') -Environment $normalEnvironment
  $processes += $normalBot
  Wait-LocalPlayers -Names @('ObeliskNormalHit')
  Start-Sleep -Seconds 4
  $null = Invoke-LocalRcon -CommandText 'gamemode survival ObeliskNormalHit'
  $null = Invoke-LocalRcon -CommandText 'clear ObeliskNormalHit'
  $null = Invoke-LocalRcon -CommandText 'give ObeliskNormalHit minecraft:diamond_sword'
  $normalPosition = Get-BossPosition -BossUuid $boss.Uuid
  $null = Invoke-LocalRcon -CommandText ("tp ObeliskNormalHit " +
    ($normalPosition[0] + 1.8D).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
    $normalPosition[1].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
    $normalPosition[2].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 90 0')
  $normalDeadline = (Get-Date).AddSeconds(18)
  while (-not $normalBot.Process.HasExited -and (Get-Date) -lt $normalDeadline) {
    Start-Sleep -Milliseconds 250
  }
  if (-not $normalBot.Process.HasExited) {
    $normalBot.Process.Kill()
    $normalBot.Process.WaitForExit(5000)
    throw 'The ordinary post-obelisk survival client timed out.'
  }
  $normalOutput = Save-ProbeOutput -Probe $normalBot -Name 'ObeliskNormalHit'
  if ($normalBot.Process.ExitCode -ne 0 -or $normalOutput.Output -notmatch 'PLAYER_END .*attacks=[1-9]') {
    throw "The ordinary post-obelisk survival client failed.`n$($normalOutput.Output)`n$($normalOutput.Error)"
  }
  $normalAfter = Get-BossSnapshot
  if ($normalAfter.Health -ge $normalBefore.Health) {
    throw "Ordinary player damage stopped working after Rift Fireball: before=$($normalBefore.Health) after=$($normalAfter.Health)"
  }
  $normalLog = Read-SharedText -Path $paperLog
  $normalDamageCount = ([Regex]::Matches($normalLog,
    'BOSS_DAMAGE_EVENT .*boss=' + [Regex]::Escape($boss.Uuid) + '.*source=PLAYER:')).Count
  if ($normalDamageCount -lt 1) {
    throw "No ordinary player boss damage event was recorded after the obelisk test. See $paperLog"
  }
  Write-Output "LIVE_RIFT_OBELISK_NORMAL_DAMAGE_PASS before=$($normalBefore.Health) after=$($normalAfter.Health) player_damage_events=$normalDamageCount"
} finally {
  foreach ($probe in $obeliskBots + @($normalBot)) {
    if ($probe -and -not $probe.Process.HasExited) {
      try { $probe.Process.Kill() } catch { }
    }
  }
  foreach ($probe in $obeliskBots + @($normalBot)) {
    if ($probe) {
      try { $probe.Process.WaitForExit(5000) | Out-Null } catch { }
    }
  }
  for ($index = 0; $index -lt $obeliskBots.Count; $index++) {
    try { Save-ProbeOutput -Probe $obeliskBots[$index] -Name $playerNames[$index] | Out-Null } catch { }
  }
  try { $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup' } catch { }
  try {
    $cleanup = Invoke-LocalRcon -CommandText 'cmend status'
    $cleanupFailed = $cleanup -notmatch 'boss=.*none' -or $cleanup -notmatch 'rift-obelisks=.*0/4' -or
      $cleanup -notmatch 'rift-fireballs=.*0'
    if ($cleanupFailed) {
      throw "Rift Obelisk cleanup left runtime state behind:`n$cleanup"
    }
    Write-Output 'LIVE_RIFT_OBELISK_CLEANUP_PASS boss=none rift-obelisks=0/4 rift-fireballs=0'
  } catch {
    throw
  }
}
