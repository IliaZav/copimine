[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'ObeliskProbeA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'ObeliskProbeB',
  [int]$BotDurationSeconds = 55,
  [int]$TimeoutSeconds = 45
)

# Local-only Wave 4 integration probe. It uses two real Mineflayer player
# connections and the normal Paper use_entity path. It never edits the map,
# the event snapshot, or a production server.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftObeliskBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLogDirectory = Join-Path $runtimeRoot 'rift-obelisk-bots'
$names = @($FirstBotName, $SecondBotName)
$probes = @()

if (@($names | Select-Object -Unique).Count -ne $names.Count) {
  throw 'The Wave 4 obelisk probe requires unique player names.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused to run outside codex/end-rift-event: $branch"
}
$config = Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw
if ($config -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: the Wave 4 probe requires environment: local.'
}
foreach ($path in @($rconScript, $botScript, $paperLog)) {
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
  $stream = [IO.File]::Open($paperLog, [IO.FileMode]::Open, [IO.FileAccess]::Read,
    [IO.FileShare]::ReadWrite)
  try {
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally { $stream.Dispose() }
}

function Get-LogLength { return (Read-SharedText).Length }

function Wait-Log {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][long]$AfterOffset,
    [int]$WaitSeconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $text = Read-SharedText
    if ($AfterOffset -lt $text.Length) {
      $tail = $text.Substring([int]$AfterOffset)
      if ([Regex]::IsMatch($tail, $Pattern)) { return $tail }
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'. See $paperLog"
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
    $text = Read-SharedText
    $tail = if ($AfterOffset -lt $text.Length) {
      $text.Substring([int]$AfterOffset)
    } else { '' }
    if (([Regex]::Matches($tail, $Pattern)).Count -ge $Minimum) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for $Minimum matches of '$Pattern'. See $paperLog"
}

function Wait-Players {
  param([Parameter(Mandatory = $true)][string[]]$Players)
  for ($attempt = 0; $attempt -lt 80; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($Players | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Probe players did not join: $($Players -join ', ')"
}

function Start-Bot {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][bool]$Reflect,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [Parameter(Mandatory = $true)][double]$CoreX,
    [Parameter(Mandatory = $true)][double]$CoreZ
  )
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $info = [Diagnostics.ProcessStartInfo]::new()
  $info.FileName = $node
  $info.WorkingDirectory = $root
  $info.UseShellExecute = $false
  $info.CreateNoWindow = $true
  $info.RedirectStandardOutput = $true
  $info.RedirectStandardError = $true
  if ($null -ne $info.ArgumentList) {
    $info.ArgumentList.Add($botScript)
    $info.ArgumentList.Add($Name)
    $info.ArgumentList.Add(([string]($BotDurationSeconds * 1000)))
  } else {
    $info.Arguments = '"' + $botScript + '" "' + $Name + '" ' +
      ([string]($BotDurationSeconds * 1000))
  }
  $info.EnvironmentVariables['END_RIFT_BOT_HOST'] = '127.0.0.1'
  $info.EnvironmentVariables['END_RIFT_BOT_PORT'] = '25566'
  $info.EnvironmentVariables['END_RIFT_REFLECT_ENABLED'] = if ($Reflect) { '1' } else { '0' }
  $info.EnvironmentVariables['END_RIFT_REFLECT_AFTER_FIREBALLS'] = '0'
  $info.EnvironmentVariables['END_RIFT_REFLECT_START_MS'] = '0'
  $info.EnvironmentVariables['END_RIFT_SKIP_AUTH_CHAT'] = '0'
  $info.EnvironmentVariables['END_RIFT_CORE_X'] = $CoreX.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
  $info.EnvironmentVariables['END_RIFT_CORE_Z'] = $CoreZ.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $info
  $process.Start() | Out-Null
  return [pscustomobject]@{
    Name = $Name
    Process = $process
    OutputTask = $process.StandardOutput.ReadToEndAsync()
    ErrorTask = $process.StandardError.ReadToEndAsync()
    OutputPath = $OutputPath
  }
}

function Save-BotOutput {
  param([Parameter(Mandatory = $true)][object]$Probe)
  $out = $Probe.OutputTask.GetAwaiter().GetResult()
  $err = $Probe.ErrorTask.GetAwaiter().GetResult()
  [IO.File]::WriteAllText($Probe.OutputPath, $out, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText(($Probe.OutputPath -replace '\.log$', '.err.log'), $err,
    [Text.UTF8Encoding]::new($false))
  return [pscustomobject]@{ Output = $out; Error = $err }
}

try {
  New-Item -ItemType Directory -Force -Path $botLogDirectory | Out-Null
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'gamemode survival @a'

  $status = Invoke-LocalRcon 'cmend status'
  if ($status -notmatch '(?m)core=.*?\s(-?\d+),(-?\d+),(-?\d+)') {
    throw "Cannot determine the local Core position for the reflection probe:`n$status"
  }
  $coreX = [double]$Matches[1] + 0.5D
  $coreZ = [double]$Matches[3] + 0.5D

  $probes += Start-Bot -Name $names[0] -Reflect $true `
    -OutputPath (Join-Path $botLogDirectory ($names[0] + '.log')) -CoreX $coreX -CoreZ $coreZ
  # Both clients are real participants, but only one sends reflection packets.
  # This removes a test-harness race where the second client correctly hit an
  # already-reflected projectile and consumed the next observation window.
  $probes += Start-Bot -Name $names[1] -Reflect $false `
    -OutputPath (Join-Path $botLogDirectory ($names[1] + '.log')) -CoreX $coreX -CoreZ $coreZ
  Wait-Players -Players $names
  Start-Sleep -Seconds 3

  foreach ($name in $names) {
    $null = Invoke-LocalRcon ("gamemode survival $name")
    $null = Invoke-LocalRcon ("clear $name")
    $null = Invoke-LocalRcon ("attribute $name minecraft:generic.max_health base set 100000")
    $null = Invoke-LocalRcon ("attribute $name minecraft:generic.knockback_resistance base set 1")
    # Keep the probe near the descending projectile's melee window. Slow
    # Falling is a player-side test harness adjustment only; it does not
    # change the world or event rules.
    $null = Invoke-LocalRcon ("effect clear $name")
    $null = Invoke-LocalRcon ("effect give $name minecraft:slow_falling 120 0 true")
    $null = Invoke-LocalRcon ("data merge entity $name {Health:100000f}")
  }
  # Hold both probes beside the north anchor. The server still chooses the
  # target normally; stable height gives a real attack packet a short,
  # deterministic melee window on the descending fireball without editing
  # arena blocks.
  $positions = @(
    @(8.5D, 70.0D, -46.0D),
    @(8.5D, 70.0D, -46.0D)
  )
  for ($index = 0; $index -lt $names.Count; $index++) {
    $pos = $positions[$index]
    $null = Invoke-LocalRcon ("tp " + $names[$index] + ' ' +
      $pos[0].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
      $pos[1].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' ' +
      $pos[2].ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 90 0')
  }

  $waveOffset = Get-LogLength
  $null = Invoke-LocalRcon 'cmend test wave 4'
  Wait-Log -Pattern 'WAVE_OBJECTIVE_STARTED .*wave=4 .*objective=OBELISK_ASSAULT' `
    -AfterOffset $waveOffset | Out-Null
  Wait-Log -Pattern 'END_RIFT_OBELISK_ASSAULT_READY .*players=2 .*obelisks=4 .*real_blocks=true' `
    -AfterOffset $waveOffset | Out-Null
  Wait-LogCount -Pattern 'END_RIFT_OBELISK_ACTIVE ' -Minimum 4 -AfterOffset $waveOffset | Out-Null
  Wait-Log -Pattern 'END_RIFT_OBELISK_PULSE .*radius=5\.0 .*effects_ticks=60' `
    -AfterOffset $waveOffset | Out-Null
  Wait-Log -Pattern 'RIFT_FIREBALL_LAUNCH .*reflected=false .*cap=1' `
    -AfterOffset $waveOffset -WaitSeconds 30 | Out-Null
  Wait-LogCount -Pattern 'RIFT_FIREBALL_REFLECTED ' -Minimum 3 `
    -AfterOffset $waveOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-Log -Pattern 'END_RIFT_OBELISK_REFLECTED_HIT .*remaining_health=2 destroyed=false' `
    -AfterOffset $waveOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-Log -Pattern 'END_RIFT_OBELISK_REFLECTED_HIT .*remaining_health=1 destroyed=false' `
    -AfterOffset $waveOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-Log -Pattern 'END_RIFT_OBELISK_REFLECTED_HIT .*remaining_health=0 destroyed=true' `
    -AfterOffset $waveOffset -WaitSeconds $BotDurationSeconds | Out-Null
  Wait-Log -Pattern 'END_RIFT_OBELISK_CLEANUP .*reason=destroyed' `
    -AfterOffset $waveOffset -WaitSeconds $BotDurationSeconds | Out-Null

  $after = Invoke-LocalRcon 'cmend status'
  $plain = $after -replace '\u00A7.', ''
  if ($plain -notmatch 'rift-obelisks=3/6') {
    throw "Expected one of four obelisks to be destroyed by reflected fireballs:`n$after"
  }
  Write-Output 'LIVE_RIFT_WAVE4_OBELISK_PASS players=2 obelisks=4 active_before=4 reflected_hits=3 first_target_hp=2 second_target_hp=1 destroyed=true pulse_radius=5 fireball_cap=1 real_blocks=true'
}
finally {
  foreach ($probe in $probes) {
    if ($probe -and -not $probe.Process.HasExited) {
      try { $probe.Process.Kill() } catch { }
    }
  }
  foreach ($probe in $probes) {
    if ($probe) {
      try { $probe.Process.WaitForExit(5000) | Out-Null } catch { }
      try { Save-BotOutput -Probe $probe | Out-Null } catch { }
    }
  }
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
}
