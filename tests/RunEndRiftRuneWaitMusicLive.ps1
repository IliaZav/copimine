[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$FirstBotName = 'EndRiftWaitA',
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$SecondBotName = 'EndRiftWaitB',
  [ValidateRange(30, 240)]
  [int]$BotDurationSeconds = 90,
  [ValidateRange(5, 60)]
  [int]$TimeoutSeconds = 30
)

# Local-only behavior probe for the real rune ritual.  The two disposable
# protocol clients stand on the persisted pads, which must start the current
# START_RITUAL hold before the first wave.  The disposable clients
# stand on the persisted pads and cause the automatic pre-fight music to be
# sent by the event controller.
# The test cancels only that local ritual in finally; it never resets the world
# or touches the production server/database.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
. (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')
$runtimeRoot = Assert-EndRiftLocalRuntimePath -Path $runtimeRoot -LocalRuntimeRoot $runtimeRoot -MustExist
$serverDir = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtimeRoot 'end-rift-server') -LocalRuntimeRoot $runtimeRoot -MustExist
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBot.js'
$paperLog = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $serverDir 'logs\latest.log') -LocalRuntimeRoot $runtimeRoot -MustExist
$statePath = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml') `
  -LocalRuntimeRoot $runtimeRoot -MustExist
$evidencePath = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtimeRoot 'rune-wait-music-live.log') -LocalRuntimeRoot $runtimeRoot
$botLogDirectory = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtimeRoot 'rune-wait-music-bots') -LocalRuntimeRoot $runtimeRoot
$playerNames = @($FirstBotName, $SecondBotName)

function Assert-UnderRuntime {
  param([Parameter(Mandatory = $true)][string]$Path)
  return Assert-EndRiftLocalRuntimePath -Path $Path -LocalRuntimeRoot $runtimeRoot
}

function Assert-LocalScope {
  $configPath = Join-Path $root 'copimine-end-event\config.yml'
  if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'Rune wait music probe refused a non-local End Rift configuration.'
  }
  $properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
      $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'Rune wait music probe requires isolated 25566/25576 ports.'
  }
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -notin @('codex/end-rift-event', 'codex/end-rift-26-3-integrated-final')) {
    throw "Rune wait music probe refused Git branch '$branch'."
  }
  foreach ($path in @($botScript, $paperLog, $statePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      throw "Rune wait music probe input is missing: $path"
    }
  }
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Rune wait music RCON failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Get-PadCoordinates {
  $yaml = Get-Content -LiteralPath $statePath -Raw
  $matches = [Regex]::Matches($yaml,
    '(?m)^-\s*x:\s*(-?\d+)\s*\r?\n\s*y:\s*(-?\d+)\s*\r?\n\s*z:\s*(-?\d+)')
  if ($matches.Count -lt $playerNames.Count) {
    throw "Local event state has $($matches.Count) rune pads; expected $($playerNames.Count)."
  }
  $coordinates = @()
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $coordinates += ,@(
      [int]$matches[$index].Groups[1].Value,
      [int]$matches[$index].Groups[2].Value,
      [int]$matches[$index].Groups[3].Value
    )
  }
  return ,$coordinates
}

function Get-LogByteOffset {
  return [int64](Get-Item -LiteralPath $paperLog).Length
}

function Read-LogSince {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $stream = [IO.File]::Open($paperLog, [IO.FileMode]::Open, [IO.FileAccess]::Read,
    [IO.FileShare]::ReadWrite)
  try {
    if ($Offset -gt $stream.Length) {
      $Offset = 0L
    }
    $stream.Seek($Offset, [IO.SeekOrigin]::Begin) | Out-Null
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false), $true)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  } finally {
    $stream.Dispose()
  }
}

function Receive-EndRiftRuneBotOutput {
  foreach ($client in $botClients) {
    foreach ($streamKind in @('Stdout', 'Stderr')) {
      if ($streamKind -eq 'Stdout') {
        $taskProperty = 'StdoutTask'
        $pathProperty = 'StdoutPath'
        $reader = $client.Process.StandardOutput
      } else {
        $taskProperty = 'StderrTask'
        $pathProperty = 'StderrPath'
        $reader = $client.Process.StandardError
      }

      $task = $client.$taskProperty
      while ($null -ne $task -and $task.IsCompleted) {
        $line = $task.GetAwaiter().GetResult()
        if ($null -eq $line) {
          $client.$taskProperty = $null
          break
        }
        [IO.File]::AppendAllText(
          $client.$pathProperty,
          $line + [Environment]::NewLine,
          [Text.UTF8Encoding]::new($false))
        $task = $reader.ReadLineAsync()
        $client.$taskProperty = $task
      }
    }
  }
}
function Wait-PlayersOnline {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    Receive-EndRiftRuneBotOutput
    $list = Invoke-LocalRcon 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Rune wait music bots did not join: $($playerNames -join ', ')"
}

function Teleport-ToPad {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][int[]]$Pad
  )
  $null = Invoke-LocalRcon ("tp $Name $($Pad[0] + 0.5) $($Pad[1]) $($Pad[2] + 0.5) 180 0")
}

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  $safeEvidencePath = Assert-EndRiftLocalRuntimePath `
    -Path $evidencePath -LocalRuntimeRoot $runtimeRoot
  Add-Content -LiteralPath $safeEvidencePath -Value $Text -Encoding UTF8
}

Assert-LocalScope
Assert-EndRiftLocalAuthMeDisabled -PluginListOutput (Invoke-LocalRcon 'plugins')
$processes = @()
$pads = $null
$logOffset = Get-LogByteOffset
$logEvidence = [Text.StringBuilder]::new()
$node = (Get-Command node.exe -ErrorAction Stop).Source
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  $botLogDirectory = Assert-EndRiftLocalRuntimePath `
    -Path $botLogDirectory -LocalRuntimeRoot $runtimeRoot -MustExist
  $status = Invoke-LocalRcon 'cmend status'
  if ($status -notmatch 'state=.*READY_FOR_PLAYERS' -or
      $status -notmatch 'boss=.*none' -or $status -notmatch 'event-mobs=.*0') {
    throw "Rune wait music probe requires a clean READY_FOR_PLAYERS scene.`n$status"
  }
  $pads = Get-PadCoordinates
  $botClients = @()
  foreach ($name in $playerNames) {
    $botLog = Assert-EndRiftLocalRuntimePath `
      -Path (Join-Path $botLogDirectory ($name + '.out.log')) -LocalRuntimeRoot $runtimeRoot
    $botErr = Assert-EndRiftLocalRuntimePath `
      -Path (Join-Path $botLogDirectory ($name + '.err.log')) -LocalRuntimeRoot $runtimeRoot
    [IO.File]::WriteAllText($botLog, '')
    [IO.File]::WriteAllText($botErr, '')
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $node
    $startInfo.Arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]($BotDurationSeconds * 1000))
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $script:runeWaitMusicStartedProcess = $null
    Invoke-EndRiftLocalBotEnvironment -BotHost '127.0.0.1' -BotPort 25566 -Action {
      Set-EndRiftLocalBotChildEnvironment -StartInfo $startInfo
      $startInfo.EnvironmentVariables['END_RIFT_BOT_LOG_SOUND'] = '1'
      $process = [Diagnostics.Process]::new()
      $process.StartInfo = $startInfo
      if (-not $process.Start()) {
        throw "Could not start the local rune-wait bot: $name"
      }
      $script:runeWaitMusicStartedProcess = $process
    }
    $process = $script:runeWaitMusicStartedProcess
    $script:runeWaitMusicStartedProcess = $null
    if ($null -eq $process) {
      throw "No local rune-wait bot process was created: $name"
    }
    $processes += $process
    $botClients += [pscustomobject]@{
      Name = $name
      Process = $process
      StdoutPath = $botLog
      StderrPath = $botErr
      StdoutTask = $process.StandardOutput.ReadLineAsync()
      StderrTask = $process.StandardError.ReadLineAsync()
    }
    $launchDeadline = (Get-Date).AddSeconds(3)
    while ((Get-Date) -lt $launchDeadline) {
      Receive-EndRiftRuneBotOutput
      Start-Sleep -Milliseconds 100
    }
  }
  Wait-PlayersOnline
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    Teleport-ToPad -Name $playerNames[$index] -Pad $pads[$index]
  }

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $ritualSeen = $false
  $musicSeen = $false
  while ((Get-Date) -lt $deadline) {
    Receive-EndRiftRuneBotOutput
    $chunk = Read-LogSince -Offset $logOffset
    $logOffset = Get-LogByteOffset
    if (-not [string]::IsNullOrEmpty($chunk)) {
      [void]$logEvidence.Append($chunk)
    }
    $status = Invoke-LocalRcon 'cmend status'
    $plainStatus = $status -replace '\u00A7.', ''
    if ($plainStatus -match 'state=START_RITUAL' -and $plainStatus -match 'pads=2/2') {
      $ritualSeen = $true
    }
    Receive-EndRiftRuneBotOutput
    $botSoundEvidence = ($playerNames | ForEach-Object {
        $path = Assert-EndRiftLocalRuntimePath `
          -Path (Join-Path $botLogDirectory ($_ + '.out.log')) -LocalRuntimeRoot $runtimeRoot
        if (Test-Path -LiteralPath $path) { Get-Content -LiteralPath $path -Raw }
      }) -join "`n"
    if ($botSoundEvidence -match 'SOUND_PACKET .*sound_effect.*ritual_wait') {
      $musicSeen = $true
    }
    if ($ritualSeen -and $musicSeen) {
      Write-Evidence 'LIVE_RUNE_WAIT_MUSIC_PASS state=START_RITUAL pads=2/2 track=copimine:end_rift/ritual_wait loop_seconds=22 client_sound_packet=true'
      Write-Evidence ($plainStatus -replace '\r?\n', ' ')
      break
    }
    Start-Sleep -Milliseconds 500
  }
  if (-not $ritualSeen) {
    throw "Rune occupancy did not start START_RITUAL within $TimeoutSeconds seconds.`n$(Invoke-LocalRcon 'cmend status')"
  }
  if (-not $musicSeen) {
    throw "Automatic ritual wait music marker was not emitted within $TimeoutSeconds seconds.`n$($logEvidence.ToString())"
  }
} finally {
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) {
      try { $process.Kill() } catch { }
    }
  }
  foreach ($process in $processes) {
    if ($process) {
      try { $process.WaitForExit(5000) | Out-Null } catch { }
      try { Receive-EndRiftRuneBotOutput } catch { }
    }
  }
  try { $null = Invoke-LocalRcon 'cmend ritual cancel confirm' } catch { }
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
}

# Give PlayerQuitEvent and the next controller tick time to repaint the pads;
# a successful wait test must leave the local scene idle rather than merely
# dropping the clients and accepting stale occupied rune displays.
Start-Sleep -Seconds 2
$cleanupStatus = Invoke-LocalRcon 'cmend status'
$cleanupPlainStatus = $cleanupStatus -replace '\u00A7.', ''
if (($cleanupPlainStatus -notmatch 'state=READY_FOR_PLAYERS') -or
    ($cleanupPlainStatus -notmatch 'pads=0/2') -or
    ($cleanupPlainStatus -notmatch 'runes=2/2 occupied=0') -or
    ($cleanupPlainStatus -notmatch 'event-mobs=0') -or
    ($cleanupPlainStatus -notmatch 'boss=none')) {
  throw "Rune wait music probe did not leave a clean local scene.`n$cleanupPlainStatus"
}
Write-Evidence 'LIVE_RUNE_WAIT_MUSIC_CLEANUP_PASS state=READY_FOR_PLAYERS pads=0/2 occupied=0 event-mobs=0 boss=none'
