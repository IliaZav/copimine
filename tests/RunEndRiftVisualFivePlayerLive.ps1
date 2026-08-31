[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$ViewerName = 'SudoKillDash9',
  [ValidateRange(60, 900)]
  [int]$BotDurationSeconds = 180,
  [ValidateRange(30, 1800)]
  [int]$TimeoutSeconds = 240
)

# Local-only visual/effects pass: five disposable Mineflayer clients plus one
# real Minecraft client used as the camera.  The creative runner is the
# production visual path without changing the official roster or rewards.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$evidencePath = Join-Path $runtimeRoot 'visual-five-player-live.log'
$botLogDirectory = Join-Path $runtimeRoot 'visual-five-player-bots'
$botNames = @('EndRiftVisualA', 'EndRiftVisualB', 'EndRiftVisualC', 'EndRiftVisualD', 'EndRiftVisualE')
$musicPhases = @(
  'ritual-wait', 'wave-1', 'intermission-1', 'wave-2', 'intermission-2', 'wave-3',
  'intermission-3', 'wave-4', 'intermission-4', 'wave-5', 'boss-cinematic',
  'final-wave', 'boss', 'boss-half', 'boss-finish', 'victory'
)

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Visual driver RCON failed: $CommandText`n$result"
  }
  return $result.Trim()
}

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

function Get-LogByteLength {
  return [int64](Get-Item -LiteralPath $paperLog).Length
}

function Get-LogTextSince {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $stream = [IO.File]::Open($paperLog, [IO.FileMode]::Open,
    [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    if ($Offset -gt $stream.Length) { $Offset = 0L }
    if ($stream.Length -le $Offset) { return '' }
    $stream.Seek($Offset, [IO.SeekOrigin]::Begin) | Out-Null
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false), $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally {
    $stream.Dispose()
  }
}

function Wait-LogRegex {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [int]$WaitSeconds = $TimeoutSeconds,
    [int64]$AfterOffset = 0
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    $raw = if ($AfterOffset -gt 0) {
      Get-LogTextSince -Offset $AfterOffset
    } else {
      Get-Content -LiteralPath $paperLog -Raw
    }
    if ($raw -match $Pattern) {
      Write-Evidence "VISUAL_LOG_PASS pattern=$Pattern after_offset=$AfterOffset"
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Visual driver timed out waiting for '$Pattern'."
}

function Assert-LocalConfiguration {
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "Visual driver refuses Git branch '$branch'."
  }
  if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'Visual driver refuses a non-local End Rift configuration.'
  }
  $properties = Get-Content -LiteralPath $propertiesPath -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
      $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'Visual driver refuses non-isolated server ports.'
  }
  if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
    throw "Paper log is missing: $paperLog"
  }
}

function Assert-OnlinePlayers {
  $list = Invoke-LocalRcon 'list'
  foreach ($name in @($ViewerName) + $botNames) {
    if ($list -notmatch [Regex]::Escape($name)) {
      throw "Visual player is not online: $name`n$list"
    }
  }
}

function Assert-ViewerOnline {
  $list = Invoke-LocalRcon 'list'
  if ($list -notmatch [Regex]::Escape($ViewerName)) {
    throw "Visual viewer is not online: $ViewerName`n$list"
  }
}

function Get-ViewerPosition {
  $response = Invoke-LocalRcon "data get entity $ViewerName Pos"
  $match = [Regex]::Match($response, '\[\s*(-?\d+(?:\.\d+)?)d?,\s*(-?\d+(?:\.\d+)?)d?,\s*(-?\d+(?:\.\d+)?)d?\s*\]')
  if (-not $match.Success) {
    return $null
  }
  return [pscustomobject]@{
    X = [double]$match.Groups[1].Value
    Y = [double]$match.Groups[2].Value
    Z = [double]$match.Groups[3].Value
  }
}

function Wait-ViewerInArena {
  for ($attempt = 0; $attempt -lt 40; $attempt++) {
    Assert-ViewerOnline
    $position = Get-ViewerPosition
    if ($position -and $position.X -ge -12.0 -and $position.X -le 28.0 `
        -and $position.Y -ge 65.0 -and $position.Y -le 71.0 `
        -and $position.Z -ge -59.0 -and $position.Z -le -19.0) {
      Write-Evidence "VISUAL_VIEWER_POSITION_PASS name=$ViewerName x=$($position.X) y=$($position.Y) z=$($position.Z)"
      return
    }
    Start-Sleep -Milliseconds 250
  }
  $positionText = Get-ViewerPosition | Out-String
  throw "Visual viewer did not settle inside the configured arena before the Creative test: $positionText"
}

Assert-LocalConfiguration
$processes = @()
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend resources reset confirm'
  $status = Invoke-LocalRcon 'cmend status'
  if ($status -notmatch 'state=.*COLLECTING') {
    throw "Visual driver requires a clean COLLECTING phase before the disposable run.`n$status"
  }
  $null = Invoke-LocalRcon 'time set day'
  $null = Invoke-LocalRcon 'weather clear'
  $null = Invoke-LocalRcon "gamemode creative $ViewerName"
  $null = Invoke-LocalRcon "tp $ViewerName 8.5 70 -57.5 0 18"
  Assert-ViewerOnline

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  foreach ($name in $botNames) {
    $botOut = Join-Path $botLogDirectory ($name + '.out.log')
    $botErr = Join-Path $botLogDirectory ($name + '.err.log')
    $arguments = '"' + $botScript + '" ' + $name + ' ' + ([string]($BotDurationSeconds * 1000)) +
      ' 8.5 68 -39 20'
    $processes += Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
      -RedirectStandardOutput $botOut -RedirectStandardError $botErr -WindowStyle Hidden -PassThru
  }
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    try {
      Assert-OnlinePlayers
      break
    } catch {
      if ($attempt -eq 89) { throw }
      Start-Sleep -Milliseconds 500
    }
  }
  Write-Evidence "VISUAL_LOCAL_SETUP_PASS viewer=$ViewerName bots=$($botNames.Count) clients=6 phase=COLLECTING"
  Wait-ViewerInArena

  foreach ($musicPhase in $musicPhases) {
    $musicOffset = Get-LogByteLength
    $null = Invoke-LocalRcon "cmend test music $musicPhase $ViewerName"
    Wait-LogRegex -Pattern ('END_EVENT_MUSIC_TEST.*track=') -WaitSeconds 15 -AfterOffset $musicOffset
    Write-Evidence "VISUAL_MUSIC_PASS phase=$musicPhase"
  }
  Write-Evidence "VISUAL_MUSIC_MATRIX_PASS phases=$($musicPhases.Count)"

  $creativeStartOffset = Get-LogByteLength
  $null = Invoke-LocalRcon "sudo $ViewerName cmend test run creative"
  Wait-LogRegex -Pattern 'CREATIVE_TEST_START' -WaitSeconds 20 -AfterOffset $creativeStartOffset

  foreach ($stage in @(
    'CREATIVE_TEST_CORE', 'CREATIVE_TEST_RESOURCES[\s\S]*CREATIVE_TEST_RUNES',
    'CREATIVE_TEST_WAVE_1', 'CREATIVE_TEST_INTERMISSION_1',
    'CREATIVE_TEST_WAVE_2', 'CREATIVE_TEST_INTERMISSION_2', 'CREATIVE_TEST_WAVE_3'
  )) {
    $stageOffset = Get-LogByteLength
    Wait-LogRegex -Pattern $stage -WaitSeconds $TimeoutSeconds -AfterOffset $stageOffset
  }

  $bossActiveOffset = Get-LogByteLength
  Wait-LogRegex -Pattern 'CREATIVE_TEST_BOSS_ACTIVE' -WaitSeconds 60 -AfterOffset $bossActiveOffset

  # The automatic boss spell stages run on the same one-second cadence as the
  # two injected spell checks below.  Keep one fresh post-boss window so a
  # stage that fires while an injected spell is being observed is not missed.
  $bossStageStartOffset = Get-LogByteLength
  $riftArrowsOffset = Get-LogByteLength
  $null = Invoke-LocalRcon 'cmend boss spell rift_arrows'
  Wait-LogRegex -Pattern 'BOSS_SPELL_CAST.*rift_arrows' -WaitSeconds 20 -AfterOffset $riftArrowsOffset
  Write-Evidence 'VISUAL_EXTRA_SPELL_PASS spell=rift_arrows'

  $arenaInfernoOffset = Get-LogByteLength
  $null = Invoke-LocalRcon 'cmend boss spell arena_inferno'
  Wait-LogRegex -Pattern 'BOSS_SPELL_CAST.*arena_inferno' -WaitSeconds 20 -AfterOffset $arenaInfernoOffset
  Write-Evidence 'VISUAL_EXTRA_SPELL_PASS spell=arena_inferno'

  foreach ($stage in @(
    'CREATIVE_TEST_BOSS_SPELL.*spell=void_blast',
    'CREATIVE_TEST_BOSS_SPELL.*spell=rift_projectile',
    'CREATIVE_TEST_BOSS_SPELL.*spell=void_mark',
    'CREATIVE_TEST_BOSS_SPELL.*spell=summon_servants',
    'CREATIVE_TEST_HALF', 'CREATIVE_TEST_CONTROL',
    'CREATIVE_TEST_FINAL_DRAIN', 'CREATIVE_TEST_FINAL_WAVE',
    'CREATIVE_TEST_BOSS_FINISH'
  )) {
    Wait-LogRegex -Pattern $stage -WaitSeconds $TimeoutSeconds -AfterOffset $bossStageStartOffset
  }
  $completionOffset = Get-LogByteLength
  Wait-LogRegex -Pattern 'CREATIVE_TEST_COMPLETE.*success=true' -WaitSeconds 60 -AfterOffset $completionOffset
  Write-Evidence "VISUAL_FIVE_PLAYER_PASS viewer=$ViewerName bots=$($botNames.Count) clients=6 music_phases=$($musicPhases.Count) extra_spells=rift_arrows,arena_inferno"
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
  try { $null = Invoke-LocalRcon "sudo $ViewerName cmend test run creative cancel" } catch { }
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon "gamemode survival $ViewerName" } catch { }
  try { $null = Invoke-LocalRcon "tp $ViewerName 0 65 0" } catch { }
}
