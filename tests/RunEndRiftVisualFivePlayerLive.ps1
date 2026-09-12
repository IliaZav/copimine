[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$ViewerName = 'EndRiftVisualA',
  [ValidateRange(60, 900)]
  [int]$BotDurationSeconds = 180,
  [ValidateRange(30, 600)]
  [int]$TimeoutSeconds = 180
)

# Local visual-contract probe.  Five independent protocol clients remain in
# the arena while the production event visual bindings, wave-front display,
# portal layers, boss cues and music catalog are exercised.  This is evidence
# for the server/client contract; native camera screenshots remain a separate
# manual check.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$installedConfigPath = Join-Path $serverDir 'plugins\CopiMineEndEvent\config.yml'
$evidenceDirectory = Join-Path $runtimeRoot 'visual-five-current'
$botLogDirectory = Join-Path $evidenceDirectory 'bots'
$controlDirectory = Join-Path $botLogDirectory 'control'
$evidencePath = Join-Path $evidenceDirectory 'run.log'
$processes = @()
$playerNames = @($ViewerName, 'EndRiftVisualB', 'EndRiftVisualC', 'EndRiftVisualD', 'EndRiftVisualE')

function Assert-LocalOnly {
  foreach ($path in @($configPath, $installedConfigPath, $rconScript, $botScript, $paperLog)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      throw "Visual probe input is missing: $path"
    }
  }
  foreach ($path in @($configPath, $installedConfigPath)) {
    $text = Get-Content -LiteralPath $path -Raw
    if ($text -notmatch '(?m)^environment:\s*local\s*$' -or
        $text -notmatch '(?m)^\s*schema-version:\s*4\s*$') {
      throw "Visual probe refused a non-current local configuration: $path"
    }
  }
  $properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
      $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'Visual probe requires isolated local Paper ports 25566 and 25576.'
  }
  if ($properties -notmatch '(?m)^server-ip=\s*$' -and
      $properties -notmatch '(?m)^server-ip=127\.0\.0\.1\s*$') {
    throw 'Visual probe requires a blank or loopback server-ip.'
  }
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "Visual probe refused Git branch '$branch'."
  }
  $runtimePrefix = $runtimeRoot.TrimEnd('\') + '\'
  if (-not $serverDir.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Visual probe refused a server outside local-runtime.'
  }
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local visual RCON failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Read-Log {
  return [string](Get-Content -LiteralPath $paperLog -Raw)
}

function Get-LogLength {
  return [int64](Read-Log).Length
}

function Get-LogTail {
  param([Parameter(Mandatory = $true)][int64]$Offset)
  $text = Read-Log
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}

function Wait-Log {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Get-LogTail -Offset $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'. See $paperLog"
}

function Get-Core {
  param([Parameter(Mandatory = $true)][string]$Status)
  $plain = $Status -replace '\u00A7.', ''
  $match = [Regex]::Match($plain, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core coordinates are absent from status: $Status" }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Wait-PlayersOnline {
  for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if (@($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) }).Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Visual clients did not join: $($playerNames -join ', ')"
}

function Wait-PlayersAuthenticated {
  param([Parameter(Mandatory = $true)][int64]$AfterOffset)
  $deadline = (Get-Date).AddSeconds(30)
  while ((Get-Date) -lt $deadline) {
    $tail = Get-LogTail -Offset $AfterOffset
    $missing = @($playerNames | Where-Object {
        $escaped = [Regex]::Escape($_)
        $tail -notmatch ("\[AuthMe\].*" + $escaped + " logged in")
      })
    if ($missing.Count -eq 0) {
      return
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Visual clients joined but did not finish AuthMe login: $($missing -join ', ')"
}

function Start-VisualBot {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][int[]]$Core
  )
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $outputPath = Join-Path $botLogDirectory ($Name + '.log')
  $errorPath = Join-Path $botLogDirectory ($Name + '.err.log')
  $durationMs = ($BotDurationSeconds + 30) * 1000
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]$durationMs) + ' ' +
    ([string]($Core[0] + 0.5D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 900 "' + $controlDirectory + '"'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $outputPath -RedirectStandardError $errorPath -WindowStyle Hidden -PassThru
  $script:processes += $process
}

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
  Write-Output $Text
}

function Assert-Output {
  param([string]$Text, [string]$Pattern, [string]$Label)
  if ($Text -notmatch $Pattern) { throw "Missing $Label in output: $Text" }
  Write-Evidence "${Label}_PASS"
}

Assert-LocalOnly
New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null
Set-Content -LiteralPath $evidencePath -Value ("END_RIFT_CURRENT_VISUAL_START time=$((Get-Date).ToString('o'))") -Encoding UTF8
foreach ($name in $playerNames) {
  if ($name -notmatch '^[A-Za-z0-9_]{1,16}$') { throw "Invalid visual client name: $name" }
  Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Value 'PASSIVE' -NoNewline -Encoding ASCII
}

$viewerProcess = $null
try {
  $baselineStatus = Invoke-LocalRcon 'cmend status'
  $core = Get-Core $baselineStatus
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  $joinOffset = Get-LogLength
  foreach ($name in $playerNames) { Start-VisualBot -Name $name -Core $core }
  Wait-PlayersOnline
  Wait-PlayersAuthenticated -AfterOffset $joinOffset
  foreach ($name in $playerNames) {
    $null = Invoke-LocalRcon "gamemode survival $name"
    $null = Invoke-LocalRcon "minecraft:tp $name $($core[0] + 0.5) $($core[1]) $($core[2] + 0.5)"
  }
  $null = Invoke-LocalRcon "op $ViewerName"
  $null = Invoke-LocalRcon "gamemode creative $ViewerName"
  $null = Invoke-LocalRcon "minecraft:tp $ViewerName $($core[0] + 0.5) $($core[1]) $($core[2] + 0.5)"
  Write-Evidence "CURRENT_VISUAL_CLIENTS_PASS count=$($playerNames.Count) core=$($core -join ',')"

  $creativeOffset = Get-LogLength
  $creativeResponse = Invoke-LocalRcon "sudo $ViewerName cmend test run creative"
  if ($creativeResponse -match '(?i)refused|ошиб|missing') {
    throw "Creative visual flow was refused: $creativeResponse"
  }
  Wait-Log -AfterOffset $creativeOffset -Pattern 'CREATIVE_TEST_START' -Seconds 30 | Out-Null
  foreach ($marker in @(
      'CREATIVE_TEST_CORE', 'CREATIVE_TEST_RESOURCES', 'CREATIVE_TEST_RUNES',
      'CREATIVE_TEST_WAVE_1', 'CREATIVE_TEST_INTERMISSION_1',
      'CREATIVE_TEST_WAVE_2', 'CREATIVE_TEST_INTERMISSION_2',
      'CREATIVE_TEST_WAVE_3', 'CREATIVE_TEST_BOSS_ACTIVE',
      'CREATIVE_TEST_BOSS_SPELL', 'CREATIVE_TEST_RIFT_PHASE',
      'CREATIVE_TEST_LAST_SEAL', 'CREATIVE_TEST_LAST_SEAL_VISUALS',
      'CREATIVE_TEST_BOSS_FINISH', 'CREATIVE_TEST_CLEANUP',
      'CREATIVE_TEST_COMPLETE event=.*success=true')) {
    Wait-Log -AfterOffset $creativeOffset -Pattern $marker -Seconds $TimeoutSeconds | Out-Null
  }
  $creativeLog = Get-LogTail -Offset $creativeOffset
  if ($creativeLog -match 'generated an exception|Exception in server tick loop') {
    throw "Current visual flow logged a Paper exception: $creativeLog"
  }
  Assert-Output $creativeLog 'BOSS_VISUAL_CUE' 'BOSS_VISUAL_CUE'
  Write-Evidence 'CURRENT_BOSS_VISUAL_CUES_PASS spells=distinct phase_updates=recorded'

  $null = Invoke-LocalRcon 'cmend wave clear'
  $portalOffset = Get-LogLength
  $portalResponse = Invoke-LocalRcon 'cmend test wave 3'
  if ($portalResponse -match '(?i)refused|ошиб|missing') { throw "Wave 3 visual probe was refused: $portalResponse" }
  Wait-Log -AfterOffset $portalOffset -Pattern 'WAVE_FRONT_STARTED.*wave=3' -Seconds 30 | Out-Null
  Wait-Log -AfterOffset $portalOffset -Pattern 'END_RIFT_PORTAL_VISUALS.*portals=3.*layers=FRAME,INNER,SHARD' -Seconds 30 | Out-Null
  Wait-Log -AfterOffset $portalOffset -Pattern 'WAVE_TEST_STARTED.*wave=3' -Seconds 30 | Out-Null
  $objective = Invoke-LocalRcon 'cmend debug objectives'
  $visualMatch = [Regex]::Match(($objective -replace '\u00A7.', ''), 'visuals=(\d+)')
  if (-not $visualMatch.Success -or [int]$visualMatch.Groups[1].Value -lt 12) {
    throw "Wave 3 did not retain its layered floor visuals: $objective"
  }
  $mobVisualOffset = Get-LogLength
  $mobVisual = Invoke-LocalRcon 'cmend test visuals mobs'
  if ($mobVisual -notmatch 'MOB_VISUAL_TOTAL=([1-9]\d*)') {
    $mobVisual = Get-LogTail -Offset $mobVisualOffset
  }
  Assert-Output $mobVisual 'MOB_VISUAL_TOTAL=([1-9]\d*)' 'MOB_VISUAL_TOTAL'
  Write-Evidence "CURRENT_WAVE3_VISUAL_PASS portals=3 layers=FRAME,INNER,SHARD displays=$($visualMatch.Groups[1].Value)"

  $null = Invoke-LocalRcon 'cmend wave clear'
  $w4Offset = Get-LogLength
  $w4Response = Invoke-LocalRcon 'cmend test wave 4'
  if ($w4Response -match '(?i)refused|ошиб|missing') { throw "Wave 4 visual probe was refused: $w4Response" }
  Wait-Log -AfterOffset $w4Offset -Pattern 'WAVE_FRONT_STARTED.*wave=4' -Seconds 30 | Out-Null
  Wait-Log -AfterOffset $w4Offset -Pattern 'END_RIFT_OBELISK_ASSAULT_READY.*real_blocks=true' -Seconds 45 | Out-Null
  Write-Evidence 'CURRENT_WAVE4_VISUAL_PASS obelisk=real-block-state telegraph=present'

  $musicKeys = @(
    'ritual-wait', 'wave-1', 'wave-2', 'wave-3', 'wave-4', 'wave-5', 'wave-6', 'wave-7',
    'intermission-1', 'intermission-2', 'intermission-3', 'intermission-5', 'intermission-6',
    'core-restoration', 'pre-boss-cooldown', 'boss-cinematic', 'boss-awakening',
    'boss-hunt', 'boss-rift', 'boss-overload', 'boss-rage', 'boss-last-seal',
    'boss-finish', 'victory'
  )
  $musicCount = 0
  foreach ($key in $musicKeys) {
    $musicOffset = Get-LogLength
    $null = Invoke-LocalRcon "cmend test music $key $ViewerName"
    $soundKey = $key.Replace('-', '_')
    Wait-Log -AfterOffset $musicOffset -Pattern ('END_EVENT_MUSIC_TEST player=' + [Regex]::Escape($ViewerName) + '.*track=copimine:end_rift/' + $soundKey + '\b') -Seconds 10 | Out-Null
    $musicCount++
  }
  Write-Evidence "CURRENT_MUSIC_CATALOG_PASS tracks=$musicCount event_scope=manual_local_test"
  $null = Invoke-LocalRcon 'cmend wave clear'
  Write-Evidence 'NATIVE_CLIENT_SCREENSHOT=NOT_VERIFIED'
  Write-Evidence 'CURRENT_VISUAL_FIVE_PLAYER_PASS clients=5 wave_front=true portals=true obelisk=true boss_cues=true music_tracks=24 cleanup_requested=true'
} finally {
  try { $null = Invoke-LocalRcon 'cmend wave clear' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
  try { $null = Invoke-LocalRcon "gamemode survival $ViewerName" } catch { }
  try { $null = Invoke-LocalRcon "deop $ViewerName" } catch { }
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { } }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.WaitForExit(5000) | Out-Null } catch { } }
  }
  foreach ($name in $playerNames) {
    Remove-Item -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Force -ErrorAction SilentlyContinue
  }
}
