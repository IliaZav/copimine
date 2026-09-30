[CmdletBinding()]
param(
  [ValidateRange(15, 900)]
  [int]$DurationSeconds = 60,
  [ValidateRange(3, 30)]
  [int]$SampleSeconds = 5,
  [string]$OutputPath = ''
)

# Read-only five-client pressure probe. It does not start Paper, reload a
# plugin, alter blocks, or reset a world/database. The official five-player
# driver covers combat; this probe isolates connection and runtime overhead.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$evidenceDirectory = Join-Path $runtimeRoot 'performance-five-live'
$botLogDirectory = Join-Path $evidenceDirectory 'bots'
$playerNames = @('EndRiftLoadA', 'EndRiftLoadB', 'EndRiftLoadC', 'EndRiftLoadD', 'EndRiftLoadE')
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
  $OutputPath = Join-Path $evidenceDirectory 'performance-five.csv'
}

function Assert-UnderRoot {
  param([string]$Path, [string]$AllowedRoot, [string]$Label)
  $full = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $rootPath = [IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\') + '\'
  if (-not $full.StartsWith($rootPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw "$Label is outside the isolated local runtime: $Path"
  }
}

Assert-UnderRoot $serverDir $runtimeRoot 'Server directory'
Assert-UnderRoot $evidenceDirectory $runtimeRoot 'Evidence directory'
Assert-UnderRoot $OutputPath $runtimeRoot 'Performance output'
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Five-player performance probe refused a non-local End Rift configuration.'
}
$properties = Get-Content -LiteralPath $propertiesPath -Raw
if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
    $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
  throw 'Five-player performance probe requires isolated 25566/25576 ports.'
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local performance bot is missing: $botScript"
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Five-player performance probe refused Git branch '$branch'."
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Five-player performance RCON failed: $CommandText`n$result"
  }
  return $result.Trim()
}

function Get-PlayerPings {
  param([string]$Raw)
  $clean = $Raw -replace '\u00A7.', ''
  $line = ($clean -split '\r?\n' | Where-Object { $_ -match 'CLIENT_BINDINGS' } | Select-Object -First 1)
  if ($line -notmatch 'lastPlayerPings=([^\s]+)') {
    return @()
  }
  return @([Regex]::Matches($matches[1], '=([0-9]+)ms') |
    ForEach-Object { [double]$_.Groups[1].Value })
}

function Get-DiagnosticSample {
  $raw = Invoke-LocalRcon 'cmend debug packets'
  $clean = $raw -replace '\u00A7.', ''
  $rateLine = ($clean -split '\r?\n' | Where-Object { $_ -match 'RUNTIME_DIAGNOSTICS sampleIntervalMs=' } | Select-Object -First 1)
  $counterLine = ($clean -split '\r?\n' | Where-Object { $_ -match 'RUNTIME_DIAGNOSTICS ownedLiving=' } | Select-Object -First 1)
  $tps = -1.0D
  $mspt = -1.0D
  $owned = -1
  $particles = -1.0D
  if ($rateLine -match 'estimatedParticlePacketsPerSecond=([0-9.,-]+)') {
    $particles = [double]::Parse($matches[1].Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
  }
  if ($counterLine -match 'ownedLiving=(\d+).*tps=([^\s]+).*mspt=([0-9.,-]+)') {
    $owned = [int]$matches[1]
    $tpsText = ($matches[2] -split '/') | Select-Object -First 1
    $tps = [double]::Parse($tpsText.Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
    $mspt = [double]::Parse($matches[3].Replace(',', '.'), [Globalization.CultureInfo]::InvariantCulture)
  }
  $pings = @(Get-PlayerPings -Raw $raw)
  [pscustomobject]@{
    Tps = $tps
    Mspt = $mspt
    Owned = $owned
    Particles = $particles
    PingAverage = if ($pings.Count) { [Math]::Round((($pings | Measure-Object -Average).Average), 2) } else { -1 }
    PingMaximum = if ($pings.Count) { [Math]::Round((($pings | Measure-Object -Maximum).Maximum), 2) } else { -1 }
  }
}

function Assert-PlayersStillConnected {
  $list = Invoke-LocalRcon 'list'
  $missing = @($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) })
  if ($missing.Count) {
    throw "Five-player performance client disconnected: $($missing -join ', ')`n$list"
  }
}

function Get-PaperProcess {
  $listener = Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -eq $listener) { return $null }
  return Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
}

$processes = @()
$rows = [System.Collections.Generic.List[object]]::new()
$previousCpuSeconds = $null
$previousCpuTime = $null
$tpsLowStreak = 0
$msptHighStreak = 0
$node = (Get-Command node.exe -ErrorAction Stop).Source
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  $botDurationMs = ($DurationSeconds + 60) * 1000
  foreach ($name in $playerNames) {
    $botOut = Join-Path $botLogDirectory ($name + '.out.log')
    $botErr = Join-Path $botLogDirectory ($name + '.err.log')
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]$botDurationMs) + ' 0 68 0 0'
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botOut -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
  }
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) { break }
    if ($attempt -eq 89) { throw "Five-player bots did not join: $($playerNames -join ', ')`n$list" }
    Start-Sleep -Milliseconds 500
  }

  $started = Get-Date
  while (((Get-Date) - $started).TotalSeconds -lt $DurationSeconds) {
    $now = Get-Date
    Assert-PlayersStillConnected
    $diagnostics = Get-DiagnosticSample
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
      connected_players = 5
      tps_5s = $diagnostics.Tps
      mspt_5s_avg = $diagnostics.Mspt
      owned_living = $diagnostics.Owned
      estimated_particle_packets_sec = $diagnostics.Particles
      ping_avg_ms = $diagnostics.PingAverage
      ping_max_ms = $diagnostics.PingMaximum
      paper_cpu_percent = $cpuPercent
      paper_working_set_mb = $workingSetMb
    }
    $rows.Add($row)
    if ($diagnostics.Tps -ge 0 -and $diagnostics.Tps -lt 15.0D) { $tpsLowStreak++ } else { $tpsLowStreak = 0 }
    if ($diagnostics.Mspt -ge 50.0D) { $msptHighStreak++ } else { $msptHighStreak = 0 }
    if ($tpsLowStreak -ge 3 -or $msptHighStreak -ge 3) {
      throw "Five-player performance threshold breached for three consecutive samples: tps=$($diagnostics.Tps) mspt=$($diagnostics.Mspt)"
    }
    Write-Output ("PERF_FIVE_SAMPLE elapsed={0}s tps={1} mspt={2} ping={3}/{4} cpu={5}% ram={6}MB" -f
      $row.elapsed_seconds, $row.tps_5s, $row.mspt_5s_avg, $row.ping_avg_ms, $row.ping_max_ms,
      $row.paper_cpu_percent, $row.paper_working_set_mb)
    Start-Sleep -Seconds $SampleSeconds
  }

  $rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
  $validTps = @($rows | Where-Object { $_.tps_5s -ge 0 } | ForEach-Object { [double]$_.tps_5s })
  $validMspt = @($rows | Where-Object { $_.mspt_5s_avg -ge 0 } | ForEach-Object { [double]$_.mspt_5s_avg })
  $validPings = @($rows | ForEach-Object { $_.ping_avg_ms; $_.ping_max_ms } |
    Where-Object { $_ -ge 0 } | ForEach-Object { [double]$_ })
  $tpsSummary = if ($validTps.Count) { [Math]::Round((($validTps | Measure-Object -Average).Average), 2) } else { -1 }
  $msptSummary = if ($validMspt.Count) { [Math]::Round((($validMspt | Measure-Object -Maximum).Maximum), 2) } else { -1 }
  $pingSummary = if ($validPings.Count) { [Math]::Round((($validPings | Measure-Object -Maximum).Maximum), 2) } else { -1 }
  Write-Output ("PERF_FIVE_PASS duration={0}s samples={1} tps_avg={2} mspt_max={3} ping_max={4} csv={5}" -f
    $DurationSeconds, $rows.Count, $tpsSummary, $msptSummary, $pingSummary, $OutputPath)
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
