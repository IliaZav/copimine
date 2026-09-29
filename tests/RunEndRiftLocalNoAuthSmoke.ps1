[CmdletBinding()]
param(
  [ValidateRange(20, 180)]
  [int]$BotDurationSeconds = 45,
  [ValidateRange(10, 90)]
  [int]$TimeoutSeconds = 30,
  [ValidateRange(1, 65535)]
  [int]$ServerPort = 25566,
  [ValidateRange(1, 65535)]
  [int]$RconPort = 25576
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$serverPrefix = $runtimeRoot.TrimEnd('\') + '\'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$serverPropertiesPath = Join-Path $serverDir 'server.properties'
$paperLogPath = Join-Path $serverDir 'logs\latest.log'
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $PSScriptRoot 'LocalEndRiftMobCombatBot.js'
$evidenceDirectory = Join-Path $root 'artifacts\end-rift-noauth-live-20260929'
$evidencePath = Join-Path $evidenceDirectory 'no-auth-smoke.log'
$controlDirectory = Join-Path $runtimeRoot 'no-auth-smoke-controls'
$processes = [System.Collections.Generic.List[object]]::new()
$botEnvironmentNames = @(
  'END_RIFT_BOT_HOST', 'END_RIFT_BOT_PORT', 'END_RIFT_BOT_CONTROL_DIRECTORY',
  'END_RIFT_BOT_SKIP_REGISTER', 'END_RIFT_BOT_SKIP_AUTH', 'END_RIFT_REFLECT_ENABLED',
  'END_RIFT_REFLECT_START_MS', 'END_RIFT_REFLECT_DIAGNOSTICS', 'END_RIFT_BOT_WAVE7_AUTOPILOT',
  'END_RIFT_BOT_WAVE7_HOLD_POSITION', 'END_RIFT_BOT_COMBAT_TRACE', 'END_RIFT_TENTACLE_TRACE',
  'END_RIFT_GUARDIAN_PROBE_NAMES', 'END_RIFT_OBELISK_TARGETS', 'END_RIFT_BOSS_UUID'
)
$previousBotEnvironment = @{}
foreach ($name in $botEnvironmentNames) {
  $previousBotEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$botNames = @()
$before = $null
$smokePassed = $false

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
  Write-Output $Text
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort $RconPort -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local smoke RCON failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Assert-LocalOnly {
  if (-not $serverDir.StartsWith($serverPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refused a server outside this worktree local-runtime directory.'
  }
  foreach ($path in @($configPath, $serverPropertiesPath, $paperLogPath, $rconScript, $botScript)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
      throw "Local no-auth smoke input is missing: $path"
    }
  }
  $config = Get-Content -LiteralPath $configPath -Raw
  if ($config -notmatch '(?m)^environment:\s*local\s*$' -or
      $config -notmatch '(?m)^\s*schema-version:\s*4\s*$') {
    throw 'Refused a non-local or non-current End Rift configuration.'
  }
  $properties = Get-Content -LiteralPath $serverPropertiesPath -Raw
  $activeBindings = Assert-EndRiftLocalServerBind `
    -ServerDir $serverDir -ServerPort $ServerPort -RconPort $RconPort
  if ($properties -notmatch '(?m)^online-mode=false\s*$' -or
      $properties -notmatch ("(?m)^server-port=" + $ServerPort + '\s*$') -or
      $properties -notmatch ("(?m)^rcon\.port=" + $RconPort + '\s*$')) {
    throw 'Refused: no-auth smoke requires the isolated loopback offline server and its configured RCON port.'
  }
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "No-auth smoke refused Git branch '$branch'."
  }
  return $activeBindings
}

function Get-Core {
  param([Parameter(Mandatory = $true)][string]$Status)
  $plain = $Status -replace '\u00A7.', ''
  $match = [Regex]::Match($plain, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Could not read the local Core position: $Status" }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Get-StatusSnapshot {
  param([Parameter(Mandatory = $true)][string]$Status)
  $plain = $Status -replace '\u00A7.', ''
  $state = [Regex]::Match($plain, '(?m)^state=(\S+)')
  $participants = [Regex]::Match($plain, '(?m)participants=(\d+)')
  $wave = [Regex]::Match($plain, '(?m)wave=(\d+)\s+event-mobs=(\d+)\s+boss=(\S+)')
  if (-not $state.Success -or -not $participants.Success -or -not $wave.Success) {
    throw "Local event status lacks the expected state fields: $Status"
  }
  return [pscustomobject]@{
    State = $state.Groups[1].Value
    Participants = [int]$participants.Groups[1].Value
    Wave = [int]$wave.Groups[1].Value
    EventMobs = [int]$wave.Groups[2].Value
    Boss = $wave.Groups[3].Value
  }
}

function Wait-Players {
  param([Parameter(Mandatory = $true)][string[]]$Names, [Parameter(Mandatory = $true)][bool]$Online)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $list = Invoke-LocalRcon 'list'
    $present = @($Names | Where-Object { $list -match [Regex]::Escape($_) })
    if (($Online -and $present.Count -eq $Names.Count) -or
        (-not $Online -and $present.Count -eq 0)) { return }
    Start-Sleep -Milliseconds 250
  }
  $condition = if ($Online) { 'online' } else { 'offline' }
  throw "Timed out waiting for smoke bots to be $($condition): $($Names -join ', ')"
}

function Start-SmokeBot {
  param([Parameter(Mandatory = $true)][string]$Name, [Parameter(Mandatory = $true)][int[]]$Core)
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $logPath = Join-Path $runtimeRoot ($Name + '.log')
  $errorPath = Join-Path $runtimeRoot ($Name + '.err.log')
  $arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]($BotDurationSeconds * 1000)) + ' ' +
    ([string]($Core[0] + 0.5D)) + ' ' + ([string]$Core[1]) + ' ' +
    ([string]($Core[2] + 0.5D)) + ' 20 900 "' + $controlDirectory + '"'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $logPath -RedirectStandardError $errorPath -WindowStyle Hidden -PassThru
  $processes.Add($process)
}

New-Item -ItemType Directory -Path $evidenceDirectory -Force | Out-Null
Set-Content -LiteralPath $evidencePath -Value "END_RIFT_LOCAL_NO_AUTH_SMOKE_START time=$((Get-Date).ToString('o'))" -Encoding UTF8
New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null

try {
  $activeBindings = Assert-LocalOnly
  $branch = (& git -C $root branch --show-current).Trim()
  Write-Evidence "LOCAL_ONLY_PASS branch=$branch server=local-runtime/end-rift-server minecraft_port=$ServerPort minecraft_bind=$($activeBindings.MinecraftListenerAddresses -join ',') rcon_port=$RconPort rcon_bind=$($activeBindings.RconListenerAddresses -join ',')"
  $pluginList = Invoke-LocalRcon 'plugins'
  if (Test-EndRiftLocalAuthMeEnabled -PluginListOutput $pluginList) {
    throw 'Refused: this live smoke specifically requires AuthMe to be disabled on the local test server.'
  }
  Write-Evidence 'LOCAL_AUTH_MODE authme=disabled rcon_plugin_list=true'

  $beforeStatus = Invoke-LocalRcon 'cmend status'
  $before = Get-StatusSnapshot -Status $beforeStatus
  $core = Get-Core -Status $beforeStatus
  if ($before.State -ne 'COLLECTING' -or $before.Wave -ne 0 -or $before.EventMobs -le 0 -or
      $before.Boss -eq 'none') {
    throw "Refused: expected the existing static showroom state, got $($before | ConvertTo-Json -Compress)"
  }
  Write-Evidence "SHOWROOM_BASELINE_PASS state=$($before.State) participants=$($before.Participants) wave=$($before.Wave) event_mobs=$($before.EventMobs) boss=$($before.Boss)"

  $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
  $botNames = @("NoAuth${suffix}A", "NoAuth${suffix}B")
  foreach ($name in $botNames) {
    if ($name.Length -gt 16 -or $name -notmatch '^[A-Za-z0-9_]{1,16}$') {
      throw "Generated an invalid disposable bot name: $name"
    }
  }
  $oldPlayers = Invoke-LocalRcon 'list'
  if (@($botNames | Where-Object { $oldPlayers -match [Regex]::Escape($_) }).Count -gt 0) {
    throw 'Refused to use a smoke bot name that is already online.'
  }

  $env:END_RIFT_BOT_HOST = '127.0.0.1'
  $env:END_RIFT_BOT_PORT = [string]$ServerPort
  $env:END_RIFT_BOT_CONTROL_DIRECTORY = $controlDirectory
  $env:END_RIFT_BOT_SKIP_REGISTER = '1'
  $env:END_RIFT_BOT_SKIP_AUTH = '1'
  $env:END_RIFT_REFLECT_ENABLED = '0'
  $env:END_RIFT_REFLECT_START_MS = '0'
  $env:END_RIFT_REFLECT_DIAGNOSTICS = '0'
  $env:END_RIFT_BOT_WAVE7_AUTOPILOT = '0'
  $env:END_RIFT_BOT_WAVE7_HOLD_POSITION = '0'
  $env:END_RIFT_BOT_COMBAT_TRACE = '0'
  $env:END_RIFT_TENTACLE_TRACE = '0'
  $env:END_RIFT_GUARDIAN_PROBE_NAMES = ''
  $env:END_RIFT_OBELISK_TARGETS = '[]'
  $env:END_RIFT_BOSS_UUID = ''
  foreach ($name in $botNames) {
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) `
      -Value 'PASSIVE' -NoNewline -Encoding ASCII
    Start-SmokeBot -Name $name -Core $core
  }

  Wait-Players -Names $botNames -Online $true
  foreach ($name in $botNames) {
    $logPath = Join-Path $runtimeRoot ($name + '.log')
    $deadline = (Get-Date).AddSeconds(15)
    $botLog = ''
    while ((Get-Date) -lt $deadline) {
      if (Test-Path -LiteralPath $logPath -PathType Leaf) { $botLog = Get-Content -LiteralPath $logPath -Raw }
      if ($botLog -match ('PLAYER_JOIN\s+' + [Regex]::Escape($name) + '\s+uuid=') -and
          $botLog -match ('BOT_AUTH_MODE\s+' + [Regex]::Escape($name) + '\s+commands=skipped')) { break }
      Start-Sleep -Milliseconds 250
    }
    if ($botLog -notmatch ('PLAYER_JOIN\s+' + [Regex]::Escape($name) + '\s+uuid=') -or
        $botLog -notmatch ('BOT_AUTH_MODE\s+' + [Regex]::Escape($name) + '\s+commands=skipped')) {
      throw "Bot $name did not prove a joined client with AuthMe commands skipped: $botLog"
    }
    Write-Evidence "BOT_AUTH_MODE_PASS player=$name joined=true commands=skipped"
  }
  Write-Evidence "NO_AUTH_PLAYER_JOIN_PASS players=$($botNames -join ',') online_list_confirmed=true"
  $smokePassed = $true
}
finally {
  $cleanupError = $null
  foreach ($process in $processes) {
    if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { } }
  }
  foreach ($process in $processes) {
    if ($process) { try { $process.WaitForExit(10000) | Out-Null } catch { } }
  }
  if ($botNames.Count -gt 0) {
    try {
      Wait-Players -Names $botNames -Online $false
      Write-Evidence "NO_AUTH_CLIENT_CLEANUP_PASS players_offline=true"
    } catch {
      Write-Evidence "NO_AUTH_CLIENT_CLEANUP_FAIL detail=$($_.Exception.Message -replace '[\r\n]+', ' ')"
      $cleanupError = $_.Exception.Message
    }
  }
  foreach ($name in $botNames) {
    Remove-Item -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Force -ErrorAction SilentlyContinue
  }
  foreach ($name in $botEnvironmentNames) {
    if ($null -eq $previousBotEnvironment[$name]) {
      Remove-Item ("Env:" + $name) -ErrorAction SilentlyContinue
    } else {
      [Environment]::SetEnvironmentVariable($name, [string]$previousBotEnvironment[$name], 'Process')
    }
  }

  if (Test-Path -LiteralPath $serverPropertiesPath -PathType Leaf) {
    try {
      $afterStatus = Invoke-LocalRcon 'cmend status'
      $after = Get-StatusSnapshot -Status $afterStatus
      if ($null -eq $before -or $after.State -ne $before.State -or
          $after.Participants -ne $before.Participants -or $after.Wave -ne $before.Wave -or
          $after.EventMobs -ne $before.EventMobs -or $after.Boss -ne $before.Boss) {
        throw "Showroom state changed during smoke: before=$($before | ConvertTo-Json -Compress) after=$($after | ConvertTo-Json -Compress)"
      }
      Write-Evidence "SHOWROOM_PRESERVED_PASS state=$($after.State) participants=$($after.Participants) wave=$($after.Wave) event_mobs=$($after.EventMobs) boss=$($after.Boss)"
    } catch {
      Write-Evidence "SHOWROOM_PRESERVED_FAIL detail=$($_.Exception.Message -replace '[\r\n]+', ' ')"
      if ($null -eq $cleanupError) { $cleanupError = $_.Exception.Message }
    }
  }
  if ($smokePassed -and $null -eq $cleanupError) {
    Write-Evidence 'END_RIFT_LOCAL_NO_AUTH_SMOKE_PASS'
  }
  if ($null -ne $cleanupError) { throw $cleanupError }
}
