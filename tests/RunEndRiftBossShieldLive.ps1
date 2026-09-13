[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftShieldProbe',
  [ValidateRange(20, 180)]
  [int]$BotDurationSeconds = 55
)

# Local-only Paper probe.  It exercises the real Last Seal shield gate and
# the real player use_entity damage path; it never changes projectile rules.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLog = Join-Path $runtimeRoot 'boss-shield-live-bot.log'
$botErr = Join-Path $runtimeRoot 'boss-shield-live-bot.err.log'

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
  return (Invoke-LocalRcon -CommandText 'cmend status') -replace '\u00A7.', ''
}

function Get-BossUuid([string]$Status) {
  $match = [Regex]::Match($Status, 'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})')
  if (-not $match.Success) { throw "Live boss is missing from local status:`n$Status" }
  return $match.Groups[1].Value
}

function Get-BossHealth([string]$Status) {
  $match = [Regex]::Match($Status, 'hp=.*?([0-9.]+)/5000')
  if (-not $match.Success) { throw "Live boss real HP is missing from local status:`n$Status" }
  return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
}

function Get-LogText {
  return [string](Get-Content -LiteralPath $paperLog -Raw)
}

function Get-LogLength {
  return [int64](Get-LogText).Length
}

function Get-LogTail([int64]$Offset) {
  $text = Get-LogText
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}

function Wait-Log {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [ValidateRange(1, 120)]
    [int]$WaitSeconds = 30
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Get-LogTail $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for '$Pattern'. See $paperLog"
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
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if ($list -match [Regex]::Escape($BotName)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Local shield probe player did not join in time: $BotName"
}

function Wait-LocalBotAbsent {
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $list = Invoke-LocalRcon -CommandText 'list'
    if ($list -notmatch [Regex]::Escape($BotName)) { return }
    Start-Sleep -Milliseconds 250
  }
  throw "Local shield probe player did not leave in time: $BotName"
}

function Start-BossProbe([string]$TargetUuid) {
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
    $startInfo.Arguments = '"' + $botScript + '" ' + $BotName + ' ' + ([string]($BotDurationSeconds * 1000))
  }
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_UUID'] = $TargetUuid
  # Give Last Seal time to initialize guardians before the first packet.  The
  # same delay also lets the runner switch a fresh boss to HUNT before the
  # vulnerable-phase measurement begins.
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_DELAY_MS'] = '10000'
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_EVERY_MS'] = '450'
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process.Start() | Out-Null
  return [pscustomobject]@{
    Process = $process
    StdoutTask = $process.StandardOutput.ReadToEndAsync()
    StderrTask = $process.StandardError.ReadToEndAsync()
  }
}

function Stop-BossProbe($Probe) {
  if ($null -eq $Probe) { return }
  $process = $Probe.Process
  if ($process -and -not $process.HasExited) {
    try { $process.Kill() } catch { }
    try { $process.WaitForExit(5000) } catch { }
  }
  if ($process -and $Probe.StdoutTask -and $Probe.StderrTask) {
    try {
      [IO.File]::WriteAllText($botLog, $Probe.StdoutTask.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false))
      [IO.File]::WriteAllText($botErr, $Probe.StderrTask.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false))
    } catch { }
  }
  Wait-LocalBotAbsent
}

function Start-BossScenario {
  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn official confirm'
  $null = Invoke-LocalRcon -CommandText 'cmend boss freeze'
  $bossUuid = Get-BossUuid (Get-Status)
  $probe = Start-BossProbe $bossUuid
  Wait-LocalBot
  $null = Invoke-LocalRcon -CommandText ("attribute $BotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon -CommandText ("heal $BotName")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("effect give $BotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon -CommandText ("give $BotName minecraft:diamond_sword")
  $position = Get-BossPosition $bossUuid
  $null = Invoke-LocalRcon -CommandText ("tp $BotName $(Format-Coordinate ($position[0] + 1.8D)) $(Format-Coordinate $position[1]) $(Format-Coordinate $position[2]) 90 0")
  $null = Invoke-LocalRcon -CommandText ("gamemode survival $BotName")
  return [pscustomobject]@{ BossUuid = $bossUuid; Probe = $probe }
}

$bossUuid = $null
$activeProbe = $null
try {
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $shieldRun = Start-BossScenario
  $bossUuid = $shieldRun.BossUuid
  $activeProbe = $shieldRun.Probe

  # Shield ON: Last Seal with living permanent guardians must reject a real
  # player attack and leave the real entity HP unchanged.
  $shieldOffset = Get-LogLength
  $null = Invoke-LocalRcon -CommandText 'cmend boss phase last_seal'
  $shieldBefore = Get-BossHealth (Get-Status)
  $shieldTail = Wait-Log -AfterOffset $shieldOffset `
    -Pattern ('BOSS_DAMAGE_BLOCKED .*boss=' + [Regex]::Escape($bossUuid) + '.*reason=permanent-guardian-shield') `
    -WaitSeconds 25
  $shieldAfter = Get-BossHealth (Get-Status)
  if ([Math]::Abs($shieldAfter - $shieldBefore) -gt 0.05D) {
    throw "Last Seal shield changed real HP: before=$shieldBefore after=$shieldAfter"
  }

  Stop-BossProbe $activeProbe
  $activeProbe = $null
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'

  # Shield OFF: use a fresh full-health official boss. The administrative
  # phase command only moves health downward, so a Last Seal boss cannot be
  # moved back to HUNT without fabricating health.
  $vulnerableRun = Start-BossScenario
  $bossUuid = $vulnerableRun.BossUuid
  $activeProbe = $vulnerableRun.Probe
  $null = Invoke-LocalRcon -CommandText 'cmend boss phase hunt'
  Start-Sleep -Seconds 1
  $vulnerableBefore = Get-BossHealth (Get-Status)
  $vulnerableOffset = Get-LogLength
  $vulnerableTail = Wait-Log -AfterOffset $vulnerableOffset `
    -Pattern ('BOSS_DAMAGE_ACCEPTED .*boss=' + [Regex]::Escape($bossUuid) + '.*authority=entity-health') `
    -WaitSeconds 25
  $vulnerableAfter = Get-BossHealth (Get-Status)
  if ($vulnerableAfter -ge $vulnerableBefore) {
    throw "Vulnerable boss did not lose real HP: before=$vulnerableBefore after=$vulnerableAfter"
  }

  Stop-BossProbe $activeProbe
  $activeProbe = $null
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'

  # Shield restored: a fresh Last Seal instance must initialize guardians and
  # return to the same explicit shield rejection reason.
  $restoredRun = Start-BossScenario
  $bossUuid = $restoredRun.BossUuid
  $activeProbe = $restoredRun.Probe
  $null = Invoke-LocalRcon -CommandText 'cmend boss phase last_seal'
  $restoredBefore = Get-BossHealth (Get-Status)
  $restoredOffset = Get-LogLength
  $restoredTail = Wait-Log -AfterOffset $restoredOffset `
    -Pattern ('BOSS_DAMAGE_BLOCKED .*boss=' + [Regex]::Escape($bossUuid) + '.*reason=permanent-guardian-shield') `
    -WaitSeconds 25
  $restoredAfter = Get-BossHealth (Get-Status)
  if ([Math]::Abs($restoredAfter - $restoredBefore) -gt 0.05D) {
    throw "Restored Last Seal shield changed real HP: before=$restoredBefore after=$restoredAfter"
  }
  if ($shieldTail -notmatch 'BOSS_DAMAGE_BLOCKED' -or
      $vulnerableTail -notmatch 'BOSS_DAMAGE_ACCEPTED' -or
      $restoredTail -notmatch 'BOSS_DAMAGE_BLOCKED') {
    throw 'Shield probe did not observe all authoritative decision markers.'
  }
  Write-Output "LIVE_BOSS_SHIELD_PASS shield_before=$shieldBefore shield_after=$shieldAfter vulnerable_before=$vulnerableBefore vulnerable_after=$vulnerableAfter restored_before=$restoredBefore restored_after=$restoredAfter shield_boss=$($shieldRun.BossUuid) vulnerable_boss=$($vulnerableRun.BossUuid) restored_boss=$($restoredRun.BossUuid) bot=$BotName"
} finally {
  Stop-BossProbe $activeProbe
  $activeProbe = $null
  try { Invoke-LocalRcon -CommandText 'cmend boss unfreeze' | Out-Null } catch { }
  try {
    Invoke-LocalRcon -CommandText 'cmend boss kill cleanup' | Out-Null
    $cleanupStatus = Get-Status
    if ($cleanupStatus -notmatch 'boss=.*none' -or $cleanupStatus -notmatch 'event-mobs=.*0') {
      throw "Local boss shield cleanup left event entities behind:`n$cleanupStatus"
    }
    Write-Output 'LIVE_BOSS_SHIELD_CLEANUP_PASS event-mobs=0 boss=none'

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
      throw "Local boss shield scene restore failed:`n$restoredStatus"
    }
    Write-Output 'LIVE_BOSS_SHIELD_SCENE_RESTORE_PASS state=READY_FOR_PLAYERS boss=none event-mobs=0'
  } catch {
    Write-Error $_
  }
}
