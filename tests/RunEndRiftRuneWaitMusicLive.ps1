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
# protocol clients stand on the persisted pads, which must start COUNTDOWN
# and cause the automatic pre-fight music to be sent by the event controller.
# The test cancels only that local ritual in finally; it never resets the world
# or touches the production server/database.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$statePath = Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml'
$evidencePath = Join-Path $runtimeRoot 'rune-wait-music-live.log'
$botLogDirectory = Join-Path $runtimeRoot 'rune-wait-music-bots'
$playerNames = @($FirstBotName, $SecondBotName)

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
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
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

function Wait-LogPattern {
  param(
    [Parameter(Mandatory = $true)][int64]$Offset,
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][string]$Label
  )
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if ((Read-LogSince -Offset $Offset) -match $Pattern) {
      return
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Rune wait music probe timed out waiting for $Label."
}

function Wait-PlayersOnline {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $list = Invoke-LocalRcon 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Rune wait music bots did not join: $($playerNames -join ', ')"
}

function Prepare-AuthMeAccounts {
  foreach ($name in $playerNames) {
    # This is an isolated local Paper database.  Pre-registering the disposable
    # probes avoids AuthMe's concurrent /register executor race when two clients
    # connect in the same tick; the real player flow still uses /login.
    $unregisterOffset = Get-LogByteOffset
    $unregisterResponse = Invoke-LocalRcon ("authme unregister $name")
    if ($unregisterResponse -notmatch "(?i)This user isn't registered!") {
      Wait-LogPattern -Offset $unregisterOffset `
        -Pattern ("AuthMe\].*" + [Regex]::Escape($name) + " was unregistered by Rcon") `
        -Label ("AuthMe unregister $name")
    }
    $registerOffset = Get-LogByteOffset
    $null = Invoke-LocalRcon ("authme register $name endrift-local")
    Wait-LogPattern -Offset $registerOffset `
      -Pattern ("AuthMe\].*Rcon registered " + [Regex]::Escape($name)) `
      -Label ("AuthMe register $name")
  }
}

function Wait-PlayersAuthenticated {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $log = Read-LogSince -Offset $Offset
    $missing = @($playerNames | Where-Object {
        $log -notmatch ("AuthMe\].*" + [Regex]::Escape($_) + " logged in")
      })
    if ($missing.Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Rune wait music bots did not authenticate: $($playerNames -join ', ')"
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
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

Assert-LocalScope
$processes = @()
$pads = $null
$logOffset = Get-LogByteOffset
$logEvidence = [Text.StringBuilder]::new()
$node = (Get-Command node.exe -ErrorAction Stop).Source
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  $status = Invoke-LocalRcon 'cmend status'
  if ($status -notmatch 'state=.*READY_FOR_PLAYERS' -or
      $status -notmatch 'boss=.*none' -or $status -notmatch 'event-mobs=.*0') {
    throw "Rune wait music probe requires a clean READY_FOR_PLAYERS scene.`n$status"
  }
  $pads = Get-PadCoordinates
  Prepare-AuthMeAccounts
  $previousSkipRegister = $env:END_RIFT_BOT_SKIP_REGISTER
  $previousBotPassword = $env:END_RIFT_BOT_PASSWORD
  $previousLogSound = $env:END_RIFT_BOT_LOG_SOUND
  $env:END_RIFT_BOT_SKIP_REGISTER = '1'
  $env:END_RIFT_BOT_PASSWORD = 'endrift-local'
  $env:END_RIFT_BOT_LOG_SOUND = '1'
  foreach ($name in $playerNames) {
    $botLog = Join-Path $botLogDirectory ($name + '.out.log')
    $botErr = Join-Path $botLogDirectory ($name + '.err.log')
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]($BotDurationSeconds * 1000))
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botLog -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
    # AuthMe's local custom build performs account writes asynchronously; keep
    # the two registration/login sessions out of the same scheduler tick.
    Start-Sleep -Seconds 3
  }
  if ($null -eq $previousSkipRegister) { Remove-Item Env:END_RIFT_BOT_SKIP_REGISTER -ErrorAction SilentlyContinue }
  else { $env:END_RIFT_BOT_SKIP_REGISTER = $previousSkipRegister }
  if ($null -eq $previousBotPassword) { Remove-Item Env:END_RIFT_BOT_PASSWORD -ErrorAction SilentlyContinue }
  else { $env:END_RIFT_BOT_PASSWORD = $previousBotPassword }
  if ($null -eq $previousLogSound) { Remove-Item Env:END_RIFT_BOT_LOG_SOUND -ErrorAction SilentlyContinue }
  else { $env:END_RIFT_BOT_LOG_SOUND = $previousLogSound }
  Wait-PlayersOnline
  Wait-PlayersAuthenticated -Offset $logOffset
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    Teleport-ToPad -Name $playerNames[$index] -Pad $pads[$index]
  }

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $countdownSeen = $false
  $musicSeen = $false
  while ((Get-Date) -lt $deadline) {
    $chunk = Read-LogSince -Offset $logOffset
    $logOffset = Get-LogByteOffset
    if (-not [string]::IsNullOrEmpty($chunk)) {
      [void]$logEvidence.Append($chunk)
    }
    $status = Invoke-LocalRcon 'cmend status'
    $plainStatus = $status -replace '\u00A7.', ''
    if ($plainStatus -match 'state=COUNTDOWN' -and $plainStatus -match 'pads=2/2') {
      $countdownSeen = $true
    }
    $botSoundEvidence = ($playerNames | ForEach-Object {
        $path = Join-Path $botLogDirectory ($_ + '.out.log')
        if (Test-Path -LiteralPath $path) { Get-Content -LiteralPath $path -Raw }
      }) -join "`n"
    if ($botSoundEvidence -match 'SOUND_PACKET .*sound_effect.*ritual_wait') {
      $musicSeen = $true
    }
    if ($countdownSeen -and $musicSeen) {
      Write-Evidence 'LIVE_RUNE_WAIT_MUSIC_PASS state=COUNTDOWN pads=2/2 track=copimine:end_rift/ritual_wait loop_seconds=22 client_sound_packet=true'
      Write-Evidence ($plainStatus -replace '\r?\n', ' ')
      break
    }
    Start-Sleep -Milliseconds 500
  }
  if (-not $countdownSeen) {
    throw "Rune occupancy did not start COUNTDOWN within $TimeoutSeconds seconds.`n$(Invoke-LocalRcon 'cmend status')"
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
    }
  }
  try { $null = Invoke-LocalRcon 'cmend ritual cancel' } catch { }
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
