[CmdletBinding()]
param(
  [ValidateRange(15, 3600)]
  [int]$DurationSeconds = 900,
  [ValidateRange(5, 30)]
  [int]$SampleSeconds = 5,
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftPerfA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftPerfB',
  [string]$OutputPath = ''
)

# This is a local-only measurement harness.  It deliberately does not start
# Paper, issue gameplay commands, reload plugins, touch blocks, or reset any
# world/database state.  It only keeps two disposable protocol clients online
# and samples the already-running isolated server.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$propertiesPath = Join-Path $serverDir 'server.properties'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$evidenceDirectory = Join-Path $runtimeRoot 'performance-live'

function Assert-UnderRoot {
  param([string]$Path, [string]$AllowedRoot, [string]$Label)
  $full = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $rootPath = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\') + '\'
  if (-not $full.StartsWith($rootPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw "$Label is outside the isolated local runtime: $Path"
  }
}

Assert-UnderRoot -Path $serverDir -AllowedRoot $runtimeRoot -Label 'Server directory'
Assert-UnderRoot -Path $evidenceDirectory -AllowedRoot $runtimeRoot -Label 'Evidence directory'
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
  $OutputPath = Join-Path $evidenceDirectory 'performance.csv'
}
Assert-UnderRoot -Path $OutputPath -AllowedRoot $runtimeRoot -Label 'Performance output'

if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Performance probe refused a non-local End Rift configuration.'
}
if ((Get-Content -LiteralPath $propertiesPath -Raw) -notmatch '(?m)^server-port=25566\s*$' -or
    (Get-Content -LiteralPath $propertiesPath -Raw) -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Performance probe requires the isolated 25566/25576 ports.'
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local performance bot is missing: $botScript"
}

$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Performance probe refused Git branch '$branch'."
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local performance RCON failed: $CommandText`n$result"
  }
  return $result.Trim()
}

function Get-DecimalNumbers {
  param([string]$Text)
  # Use the Unicode escape instead of a literal section sign.  This keeps the
  # probe compatible with Windows PowerShell 5.1 when the file has no BOM.
  return @([Regex]::Matches(($Text -replace '\u00A7.', ''), '(?<![A-Za-z])\d+(?:[\.,]\d+)?') |
    ForEach-Object { [double]::Parse($_.Value.Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture) })
}

function Get-RuntimeDiagnosticSample {
  $raw = (Invoke-LocalRcon 'cmend debug packets') -replace '\u00A7.', ''
  $bindingLine = ($raw -split '\r?\n' | Where-Object { $_ -match 'CLIENT_BINDINGS' } | Select-Object -First 1)
  $rateLine = ($raw -split '\r?\n' | Where-Object { $_ -match 'RUNTIME_DIAGNOSTICS sampleIntervalMs=' } | Select-Object -First 1)
  $counterLine = ($raw -split '\r?\n' | Where-Object { $_ -match 'RUNTIME_DIAGNOSTICS ownedLiving=' } | Select-Object -First 1)
  $pings = @()
  if ($bindingLine -match 'lastPlayerPings=([^\s]+)') {
    $pings = @([Regex]::Matches($matches[1], '=([0-9]+)ms') |
      ForEach-Object { [double]$_.Groups[1].Value })
  }
  $tps = -1.0D
  $mspt = -1.0D
  $owned = -1
  $displays = -1
  $projectiles = -1
  $particles = -1.0D
  $pluginMessages = -1.0D
  $gcPause = -1
  $serverCpu = -1.0D
  if ($rateLine -match 'pluginMessagesPerSecond=([0-9.,-]+).*estimatedParticlePacketsPerSecond=([0-9.,-]+)') {
    $pluginMessages = [double]::Parse($matches[1].Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
    $particles = [double]::Parse($matches[2].Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
  }
  if ($counterLine -match 'ownedLiving=(\d+).*temporaryDisplays=(\d+).*activeProjectiles=(\d+).*gcPauseMs=(\d+).*serverThreadCpuPercent=([0-9.,-]+).*tps=([^\s]+).*mspt=([0-9.,-]+)') {
    $owned = [int]$matches[1]
    $displays = [int]$matches[2]
    $projectiles = [int]$matches[3]
    $gcPause = [int]$matches[4]
    $serverCpu = [double]::Parse($matches[5].Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
    $tpsValue = ($matches[6] -split '/') | Select-Object -First 1
    $tps = [double]::Parse($tpsValue.Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
    $mspt = [double]::Parse($matches[7].Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
  }
  [pscustomobject]@{
    Tps = $tps
    Mspt = $mspt
    Owned = $owned
    Displays = $displays
    Projectiles = $projectiles
    Particles = $particles
    PluginMessages = $pluginMessages
    GcPause = $gcPause
    ServerCpu = $serverCpu
    PingAverage = if ($pings.Count) { [Math]::Round((($pings | Measure-Object -Average).Average), 2) } else { -1 }
    PingMaximum = if ($pings.Count) { [Math]::Round((($pings | Measure-Object -Maximum).Maximum), 2) } else { -1 }
  }
}

function Get-EventSnapshot {
  $raw = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
  $state = if ($raw -match 'state=\s*([A-Z_]+)') { $matches[1] } else { 'UNKNOWN' }
  $phase = if ($raw -match 'wave=\s*([A-Za-z0-9_]+)') { $matches[1] } elseif ($raw -match 'state=\s*([A-Z_]+)') { $matches[1] } else { 'UNKNOWN' }
  $mobs = if ($raw -match 'event-mobs=\s*(\d+)') { [int]$matches[1] } else { -1 }
  [pscustomobject]@{ State = $state; Phase = $phase; EventMobs = $mobs }
}

function Get-PaperProcess {
  $listener = Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -eq $listener) { return $null }
  return Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
}

function Wait-Players {
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if ($list -match [Regex]::Escape($FirstBotName) -and
        $list -match [Regex]::Escape($SecondBotName)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Performance bots did not join: $FirstBotName, $SecondBotName"
}

function Assert-PlayersStillConnected {
  $list = Invoke-LocalRcon 'list'
  if ($list -notmatch [Regex]::Escape($FirstBotName) -or
      $list -notmatch [Regex]::Escape($SecondBotName)) {
    throw "A performance client disconnected during the sample window: $list"
  }
}

$processes = @()
$rows = [System.Collections.Generic.List[object]]::new()
$previousCpuSeconds = $null
$previousCpuTime = $null
$tpsLowStreak = 0
$msptHighStreak = 0
$node = (Get-Command node.exe -ErrorAction Stop).Source

try {
  New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
  foreach ($name in @($FirstBotName, $SecondBotName)) {
    $botOut = Join-Path $evidenceDirectory ($name + '.out.log')
    $botErr = Join-Path $evidenceDirectory ($name + '.err.log')
    # The Mineflayer combat bot has the same protocol/keep-alive path as the
    # successful live combat probes.  NaN arena coordinates would make it
    # attack arbitrary entities, so keep its measured arena radius at zero.
    $botDurationMs = ($DurationSeconds + 60) * 1000
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]$botDurationMs) + ' 0 68 0 0'
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botOut -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
  }
  Wait-Players

  $started = Get-Date
  $nextReport = $started.AddSeconds(30)
  while (((Get-Date) - $started).TotalSeconds -lt $DurationSeconds) {
    $now = Get-Date
    $diagnostics = Get-RuntimeDiagnosticSample
    $event = Get-EventSnapshot
    Assert-PlayersStillConnected
    $paper = Get-PaperProcess
    $cpuPercent = -1.0D
    $workingSetMb = -1.0D
    if ($null -ne $paper) {
      $cpu = $paper.TotalProcessorTime.TotalSeconds
      $workingSetMb = [Math]::Round($paper.WorkingSet64 / 1MB, 1)
      if ($null -ne $previousCpuSeconds -and $null -ne $previousCpuTime) {
        $wallSeconds = [Math]::Max(0.001D, ($now - $previousCpuTime).TotalSeconds)
        $cpuPercent = [Math]::Round((($cpu - $previousCpuSeconds) / $wallSeconds) /
          [Math]::Max(1, [Environment]::ProcessorCount) * 100.0D, 1)
      }
      $previousCpuSeconds = $cpu
      $previousCpuTime = $now
    }
    $row = [pscustomobject]@{
      timestamp = $now.ToString('o')
      elapsed_seconds = [int][Math]::Round(($now - $started).TotalSeconds)
      state = $event.State
      phase = $event.Phase
      connected_players = 2
      event_mobs = $event.EventMobs
      owned_living = $diagnostics.Owned
      temporary_displays = $diagnostics.Displays
      active_projectiles = $diagnostics.Projectiles
      estimated_particle_packets_sec = $diagnostics.Particles
      plugin_messages_sec = $diagnostics.PluginMessages
      gc_pause_ms = $diagnostics.GcPause
      server_thread_cpu_percent = $diagnostics.ServerCpu
      tps_5s = $diagnostics.Tps
      mspt_5s_avg = $diagnostics.Mspt
      ping_avg_ms = $diagnostics.PingAverage
      ping_max_ms = $diagnostics.PingMaximum
      paper_cpu_percent = $cpuPercent
      paper_working_set_mb = $workingSetMb
    }
    $rows.Add($row)
    # TPS is a rolling statistic and can remain low after an earlier probe;
    # current MSPT is the primary signal.  Keep a conservative fallback for
    # an actually stalled server without failing on a harmless warm-up dip.
    if ($diagnostics.Tps -ge 0 -and $diagnostics.Tps -lt 15.0D) { $tpsLowStreak++ } else { $tpsLowStreak = 0 }
    if ($diagnostics.Mspt -ge 50.0D) { $msptHighStreak++ } else { $msptHighStreak = 0 }
    if ($tpsLowStreak -ge 3 -or $msptHighStreak -ge 3) {
      throw "Local performance threshold breached for three consecutive samples: tps=$($diagnostics.Tps) mspt=$($diagnostics.Mspt)"
    }
    if ($now -ge $nextReport) {
      Write-Output ("PERF_SAMPLE elapsed={0}s state={1} phase={2} mobs={3} tps={4} mspt={5} ping={6}/{7} cpu={8}% ram={9}MB" -f
        $row.elapsed_seconds, $row.state, $row.phase, $row.event_mobs, $row.tps_5s, $row.mspt_5s_avg,
        $row.ping_avg_ms, $row.ping_max_ms, $row.paper_cpu_percent, $row.paper_working_set_mb)
      $nextReport = $now.AddSeconds(30)
    }
    Start-Sleep -Seconds $SampleSeconds
  }

  $rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
  $validTps = @($rows | Where-Object { $_.tps_5s -ge 0 } | ForEach-Object { [double]$_.tps_5s })
  $validMspt = @($rows | Where-Object { $_.mspt_5s_avg -ge 0 } | ForEach-Object { [double]$_.mspt_5s_avg })
  $validPings = @($rows | ForEach-Object { $_.ping_avg_ms; $_.ping_max_ms } | Where-Object { $_ -ge 0 } | ForEach-Object { [double]$_ })
  $phases = (@($rows | Select-Object -ExpandProperty phase -Unique) -join ',')
  $pingAverage = -1
  $pingMaximum = -1
  if ($validPings.Count) {
    $pingAverage = [Math]::Round((($validPings | Measure-Object -Average).Average), 2)
    $pingMaximum = [Math]::Round((($validPings | Measure-Object -Maximum).Maximum), 2)
  }
  Write-Output ("PERF_PASS duration={0}s samples={1} tps_avg={2} tps_min={3} mspt_max={4} ping_avg={5} ping_max={6} phases={7} csv={8}" -f
    $DurationSeconds, $rows.Count,
    ([Math]::Round((($validTps | Measure-Object -Average).Average), 2)),
    ([Math]::Round((($validTps | Measure-Object -Minimum).Minimum), 2)),
    ([Math]::Round((($validMspt | Measure-Object -Maximum).Maximum), 2)),
    $pingAverage,
    $pingMaximum,
    $phases, $OutputPath)
} finally {
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) {
      try { $process.Kill() } catch { }
    }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.WaitForExit(5000) | Out-Null } catch { } }
  }
}
