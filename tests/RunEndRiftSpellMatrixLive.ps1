[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'RiftSpellTest',
  # The complete matrix contains 24 music phases, seven boss spell probes,
  # recovery waits, and the mini-boss assignment checks.  Keep the disposable
  # client connected for the whole sequence so a valid final-strike probe is
  # not invalidated by the bot's own lifetime expiring.
  [ValidateRange(240, 600)]
  [int]$BotDurationSeconds = 300,
  [string]$ExpectedBranch = 'codex/end-rift-26-3-integrated-final'
)

# Local-only spell/music smoke. It talks to the isolated Paper RCON endpoint,
# never to the production address, and removes only disposable test entities.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$branchLines = @(& git -C $root branch --show-current 2>$null)
$branchExitCode = $LASTEXITCODE
$branch = if ($branchLines.Count -gt 0) { ([string]$branchLines[0]).Trim() } else { '' }
if ($branchExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($branch) -or $branch -cne $ExpectedBranch) {
  throw "Refused Git branch '$branch'."
}
$localRuntimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$runtimeRoot = Assert-EndRiftLocalRuntimePath -Path $localRuntimeRoot -LocalRuntimeRoot $localRuntimeRoot -MustExist
$serverDir = Assert-EndRiftLocalRuntimePath -Path (Join-Path $runtimeRoot 'end-rift-server') `
  -LocalRuntimeRoot $runtimeRoot -MustExist
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$controlDirectory = Join-Path $runtimeRoot 'spell-matrix-control'

$config = Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw
if ($config -notmatch '(?m)^environment:\s*local\s*$' -or
    $config -notmatch '(?m)^\s*schema-version:\s*4\s*$') {
  throw 'Refused: spell matrix requires the current local schema.'
}
if (-not (Test-Path -LiteralPath $serverDir -PathType Container)) {
  throw "Isolated Paper directory is missing: $serverDir"
}
if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
  throw "Local protocol bot is missing: $botScript"
}
$serverProperties = @(Get-Content -LiteralPath (Join-Path $serverDir 'server.properties'))
Assert-EndRiftLocalConfiguredPorts -ServerPropertiesLines $serverProperties -ServerPort 25566 -RconPort 25576 | Out-Null
Assert-EndRiftLocalServerBind -ServerDir $serverDir -ServerPort 25566 -RconPort 25576 | Out-Null
Assert-EndRiftLocalServerTreeIsolated -ServerDir $serverDir -LocalRuntimeRoot $runtimeRoot | Out-Null

$spellMatrixBotNames = @($BotName)
$botNamePrefixLength = [Math]::Min(12, $BotName.Length)
$botNamePrefix = $BotName.Substring(0, $botNamePrefixLength)
for ($index = 1; $index -le 5; $index++) {
  $candidateName = $botNamePrefix + ('S{0:D2}' -f $index)
  if ($candidateName -in $spellMatrixBotNames) {
    throw "The base bot name collides with a generated six-player probe name: $candidateName"
  }
  $spellMatrixBotNames += $candidateName
}
if ($spellMatrixBotNames.Count -ne 6 -or @($spellMatrixBotNames | Sort-Object -Unique).Count -ne 6) {
  throw 'The Wave 6 spell matrix requires six unique local bot names.'
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

function Get-LogLength {
  return ([string](Get-Content -LiteralPath $paperLog -Raw)).Length
}

function Get-NewLogText {
  param([Parameter(Mandatory = $true)][int64]$PreviousLength)
  $current = [string](Get-Content -LiteralPath $paperLog -Raw)
  if ($current.Length -le $PreviousLength) {
    return ''
  }
  return $current.Substring($PreviousLength)
}

function Get-CoreCoordinates {
  param([Parameter(Mandatory = $true)][string]$Status)
  $match = [Regex]::Match($Status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) {
    throw "Core coordinates were not exposed by local status:`n$Status"
  }
  return @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

function Test-EndRiftLocalPlayerNamesOnline {
  param(
    [Parameter(Mandatory = $true)][AllowEmptyString()][string]$PlayerListOutput,
    [Parameter(Mandatory = $true)][string[]]$RequiredNames
  )
  if ([string]::IsNullOrWhiteSpace($PlayerListOutput)) { return $false }
  foreach ($name in $RequiredNames) {
    $pattern = '(?<![A-Za-z0-9_])' + [Regex]::Escape($name) + '(?![A-Za-z0-9_])'
    if ($PlayerListOutput -notmatch $pattern) { return $false }
  }
  return $true
}

function Start-SpellMatrixBot {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][int[]]$Core
  )
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $node
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $botX = [string]($Core[0] + 6)
  $botY = [string]$Core[1]
  $botZ = [string]$Core[2]
  $durationMs = [string]($BotDurationSeconds * 1000)
  if ($null -ne $startInfo.ArgumentList) {
    foreach ($argument in @($botScript, $Name, $durationMs, $botX, $botY, $botZ, '20', '250', $controlDirectory)) {
      $startInfo.ArgumentList.Add([string]$argument)
    }
  } else {
    $startInfo.Arguments = '"' + $botScript + '" ' + $Name + ' ' + $durationMs + ' ' +
      $botX + ' ' + $botY + ' ' + $botZ + ' 20 250 "' + $controlDirectory + '"'
  }

  $script:spellMatrixStartedProcess = $null
  Invoke-EndRiftLocalBotEnvironment -BotHost '127.0.0.1' -BotPort 25566 -Action {
    Set-EndRiftLocalBotChildEnvironment -StartInfo $startInfo
    Set-EndRiftLocalBotChildEnvironmentOverrides -StartInfo $startInfo -Overrides @{
      END_RIFT_BOT_CONTROL_DIRECTORY = $controlDirectory
      END_RIFT_BOT_SKIP_AUTH = '1'
      END_RIFT_BOT_SKIP_REGISTER = '1'
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
      throw "Could not start the local spell-matrix bot: $Name"
    }
    $script:spellMatrixStartedProcess = $process
  }
  $process = $script:spellMatrixStartedProcess
  $script:spellMatrixStartedProcess = $null
  if ($null -eq $process) { throw "No local spell-matrix bot process was created: $Name" }
  $script:spellMatrixBotProcesses.Add([pscustomobject]@{
      Name = $Name
      Process = $process
      StdoutTask = $process.StandardOutput.ReadToEndAsync()
      StderrTask = $process.StandardError.ReadToEndAsync()
    })
}

function Wait-LocalBots {
  param([int]$TimeoutSeconds = 90)
  for ($attempt = 0; $attempt -lt ($TimeoutSeconds * 2); $attempt++) {
    foreach ($bot in $script:spellMatrixBotProcesses) {
      if ($bot.Process.HasExited) {
        throw "Local spell-matrix bot exited before joining: $($bot.Name) (exit=$($bot.Process.ExitCode))"
      }
    }
    $list = Invoke-LocalRcon -CommandText 'list'
    if (Test-EndRiftLocalPlayerNamesOnline -PlayerListOutput $list -RequiredNames $spellMatrixBotNames) {
      return
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Local spell-matrix bots did not all join in time: $($spellMatrixBotNames -join ', ')"
}

function Stop-SpellMatrixBots {
  foreach ($bot in $script:spellMatrixBotProcesses) {
    if ($bot.Process -and -not $bot.Process.HasExited) {
      try { $bot.Process.Kill() } catch { }
    }
  }
  foreach ($bot in $script:spellMatrixBotProcesses) {
    if ($bot.Process) {
      $exited = $false
      try { $exited = $bot.Process.WaitForExit(5000) } catch { }
      if ($exited) {
        $botLog = Assert-EndRiftLocalRuntimePath -Path (Join-Path $runtimeRoot ("spell-matrix-live-$($bot.Name).log")) -LocalRuntimeRoot $runtimeRoot
        $botErr = Assert-EndRiftLocalRuntimePath -Path (Join-Path $runtimeRoot ("spell-matrix-live-$($bot.Name).err.log")) -LocalRuntimeRoot $runtimeRoot
        if ($bot.StdoutTask) {
          try { [IO.File]::WriteAllText($botLog, $bot.StdoutTask.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { }
        }
        if ($bot.StderrTask) {
          try { [IO.File]::WriteAllText($botErr, $bot.StderrTask.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { }
        }
      } else {
        Write-Warning "Spell-matrix bot did not stop within the cleanup timeout: $($bot.Name)"
      }
    }
    Remove-Item -LiteralPath (Join-Path $controlDirectory ($bot.Name + '.mode')) -Force -ErrorAction SilentlyContinue
  }
  $script:spellMatrixBotProcesses.Clear()
}

function Wait-LogMarker {
  param(
    [Parameter(Mandatory = $true)][int64]$PreviousLength,
    [Parameter(Mandatory = $true)][string]$Pattern,
    [int]$TimeoutSeconds = 8
  )
  for ($attempt = 0; $attempt -lt ($TimeoutSeconds * 4); $attempt++) {
    $delta = Get-NewLogText -PreviousLength $PreviousLength
    if ($delta -match $Pattern) {
      return $delta
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for local log marker '$Pattern'."
}

function Assert-BossSpell {
  param(
    [Parameter(Mandatory = $true)][string]$Spell,
    [Parameter(Mandatory = $true)][int64]$PreviousLength
  )
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "BOSS_SPELL_TELEGRAPH.*spell=$Spell" -TimeoutSeconds 5
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "BOSS_SPELL_FLIGHT.*spell=$Spell" -TimeoutSeconds 5
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "BOSS_SPELL_CAST.*spell=$Spell" -TimeoutSeconds 5
  $delta = Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern "SPELL_IMPACT_VISUAL spell=$Spell" -TimeoutSeconds 5
  Write-Output "LIVE_SPELL_PASS spell=$Spell telegraph=1 flight=1 cast=1 impact=1"
}

function Assert-BossFireball {
  param([Parameter(Mandatory = $true)][int64]$PreviousLength)
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_SPELL_TELEGRAPH.*spell=boss_fireball' -TimeoutSeconds 8 | Out-Null
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_SPELL_FLIGHT.*spell=boss_fireball' -TimeoutSeconds 8 | Out-Null
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_SPELL_CAST.*spell=boss_fireball' -TimeoutSeconds 8 | Out-Null
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_FIREBALL_LAUNCH.*blocks=false.*fire=false' -TimeoutSeconds 8 | Out-Null
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'SPELL_IMPACT_VISUAL spell=boss_fireball' -TimeoutSeconds 8 | Out-Null
  Write-Output 'LIVE_SPELL_PASS spell=boss_fireball telegraph=1 flight=1 cast=1 impact=1 blocks=false fire=false'
}

function Wait-BossCastRecovery {
  # The production hazard ledger intentionally remains reserved during the
  # short post-impact recovery window.  A forced spell matrix must wait for
  # that window instead of racing the real boss controller and reporting a
  # budget rejection as a missing spell.
  Start-Sleep -Milliseconds 1300
  for ($attempt = 0; $attempt -lt 12; $attempt++) {
    $status = Invoke-LocalRcon -CommandText 'cmend boss info'
    if ($status -match '(?m)bossCast=(?:NONE|$)' -or
        $status -match '(?m)bossCastState=(?:NONE|$)') {
      return
    }
    Start-Sleep -Milliseconds 100
  }
}

function Assert-FinalStrike {
  param([Parameter(Mandatory = $true)][int64]$PreviousLength)
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_FINAL_STRIKE_START.*stage=LAST_SEAL' -TimeoutSeconds 8 | Out-Null
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_FINAL_STRIKE_IMPACT.*blocks=false' -TimeoutSeconds 8 | Out-Null
  Wait-LogMarker -PreviousLength $PreviousLength `
    -Pattern 'BOSS_FINAL_STRIKE_COMPLETE.*impacted=true' -TimeoutSeconds 8 | Out-Null
  Write-Output 'LIVE_SPELL_PASS spell=final_strike telegraph=1 impact=1 recovery=1'
}

function Set-BossPhase {
  param([Parameter(Mandatory = $true)][string]$Phase)
  if ($Phase -eq 'awakening') {
    Write-Output 'LIVE_BOSS_PHASE_PASS phase=AWAKENING initial=true'
    return
  }
  $currentStatus = Invoke-LocalRcon -CommandText 'cmend boss info'
  if ($currentStatus -match ('(?m)bossPhase=\xA7?f?' + $Phase.ToUpperInvariant() + '\b')) {
    Write-Output "LIVE_BOSS_PHASE_PASS phase=$($Phase.ToUpperInvariant()) already=true"
    return
  }
  $before = Get-LogLength
  $response = Invoke-LocalRcon -CommandText ("cmend boss phase $Phase")
  if ($response -match '(?i)refused|missing|ошиб') {
    throw "Boss phase $Phase was refused:`n$response"
  }
  Wait-LogMarker -PreviousLength $before -Pattern ("BOSS_STAGE_TRANSITION.*to=" + $Phase.ToUpperInvariant() + '\b') -TimeoutSeconds 8 | Out-Null
  Write-Output "LIVE_BOSS_PHASE_PASS phase=$($Phase.ToUpperInvariant())"
}

$script:spellMatrixBotProcesses = [System.Collections.Generic.List[object]]::new()
$script:spellMatrixStartedProcess = $null
$plugins = Invoke-LocalRcon -CommandText 'plugins'
Assert-EndRiftLocalAuthMeDisabled -PluginListOutput $plugins | Out-Null
try {
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  $core = Get-CoreCoordinates (Invoke-LocalRcon -CommandText 'cmend status')
  $matrixLogStart = Get-LogLength

  New-Item -ItemType Directory -Path $controlDirectory -Force | Out-Null
  $controlDirectory = Assert-EndRiftLocalRuntimePath -Path $controlDirectory -LocalRuntimeRoot $runtimeRoot -MustExist
  foreach ($name in $spellMatrixBotNames) {
    Remove-Item -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Force -ErrorAction SilentlyContinue
    # These are disposable combat-roster clients. Passive controls keep them
    # connected without making them fight the boss during the matrix.
    Set-Content -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) `
      -Value 'PASSIVE' -NoNewline -Encoding ASCII
  }
  foreach ($name in $spellMatrixBotNames) {
    Start-SpellMatrixBot -Name $name -Core $core
  }
  Wait-LocalBots
  foreach ($name in $spellMatrixBotNames) {
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:max_health base set 1000")
    $null = Invoke-LocalRcon -CommandText ("heal $name")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:regeneration 1000 4 true")
    $nameIndex = [Array]::IndexOf($spellMatrixBotNames, $name)
    $zOffset = ($nameIndex * 2) - 5
    $playerZ = $core[2] + $zOffset
    $null = Invoke-LocalRcon -CommandText ("tp $name $($core[0] + 6) $($core[1]) $playerZ 180 0")
  }
  Start-Sleep -Seconds 1

  # Manual phase playback is explicitly local-only. Outside combat it must
  # produce only the TEST marker; no automatic event track may start.
  $phaseKeys = @(
    'ritual-wait', 'wave-1', 'wave-2', 'wave-3', 'wave-4', 'wave-5', 'wave-6', 'wave-7',
    'intermission-1', 'intermission-2', 'intermission-3', 'intermission-5', 'intermission-6',
    'core-restoration', 'pre-boss-cooldown', 'boss-cinematic', 'boss-awakening',
    'boss-hunt', 'boss-rift', 'boss-overload', 'boss-rage', 'boss-last-seal',
    'boss-finish', 'victory'
  )
  foreach ($key in $phaseKeys) {
    $before = Get-LogLength
    # The RCON response crosses two PowerShell processes and can be decoded
    # with the host code page.  Use the plugin's UTF-8 log marker as the
    # authoritative result instead of comparing localized chat text.
    $null = Invoke-LocalRcon -CommandText ("cmend test music $key $BotName")
    $delta = Wait-LogMarker -PreviousLength $before `
      -Pattern "END_EVENT_MUSIC_TEST.*track=copimine:end_rift/$($key.Replace('-', '_'))" -TimeoutSeconds 3
    if ($delta -match 'END_EVENT_MUSIC track=') {
      throw "Automatic event music started during manual phase test $key."
    }
    Write-Output "LIVE_MUSIC_PASS phase=$key manual_test=1 automatic_outside_event=0"
  }

  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn'
  Start-Sleep -Seconds 1
  foreach ($entry in @(
    @{ Phase = 'awakening'; Spell = 'void_blast' },
    @{ Phase = 'awakening'; Spell = 'rift_projectile' },
    @{ Phase = 'hunt'; Spell = 'rift_arrows' },
    @{ Phase = 'hunt'; Spell = 'void_mark' },
    @{ Phase = 'rift'; Spell = 'summon_servants' },
    @{ Phase = 'rift'; Spell = 'boss_fireball' },
    @{ Phase = 'rage'; Spell = 'arena_inferno' },
    @{ Phase = 'last_seal'; Spell = 'final_strike' }
  )) {
    Set-BossPhase -Phase $entry.Phase
    $spell = $entry.Spell
    $before = Get-LogLength
    $response = Invoke-LocalRcon -CommandText ("cmend boss spell $spell")
    if ($response -match '(?i)refused|missing|not created') {
      throw "Boss spell $spell was refused:`n$response"
    }
    if ($spell -eq 'final_strike') {
      Assert-FinalStrike -PreviousLength $before
    } elseif ($spell -eq 'boss_fireball') {
      Assert-BossFireball -PreviousLength $before
    } else {
      Assert-BossSpell -Spell $spell -PreviousLength $before
    }
    Wait-BossCastRecovery
  }

  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'

  $wave = 6
  $beforeWave6 = Get-LogLength
  if ($wave -eq 6) {
    $wave6Response = Invoke-LocalRcon -CommandText 'cmend test wave 6 combat'
  } else {
    throw "The combat spell matrix expected Wave 6, got $wave."
  }
  if ($wave6Response -match '(?i)refused|missing|event world') {
    throw "Wave 6 combat spell probe was refused: $wave6Response"
  }
  Wait-LogMarker -PreviousLength $beforeWave6 -Pattern 'WAVE6_PRESSURE_PACK.*players=6.*planned=4.*spawned=4.*test=true' -TimeoutSeconds 12 | Out-Null
  $assignment = Wait-LogMarker -PreviousLength $beforeWave6 -Pattern "TEST_WAVE_${wave}_SPELL_ASSIGNMENTS.*miniBossSpells=\[[^\]]*\]" -TimeoutSeconds 12
  $assignmentMatch = [Regex]::Match($assignment, 'miniBossSpells=\[(?<spells>[^\]]*)\]')
  if (-not $assignmentMatch.Success) {
    throw "Wave 6 did not publish its assigned mini-boss spell set. $assignment"
  }
  $wave6Spells = @([Regex]::Matches($assignmentMatch.Groups['spells'].Value, '[a-z][a-z_]*') |
    ForEach-Object { $_.Value } | Sort-Object -Unique)
  $requiredWave6SpellIds = @('arrow_rain', 'void_burst', 'web_shot')
  $missingWave6Spells = @($requiredWave6SpellIds | Where-Object { $_ -notin $wave6Spells })
  if ($missingWave6Spells.Count -gt 0) {
    throw "Wave 6 did not assign all required physical elite spells: $($missingWave6Spells -join ', '). $assignment"
  }
  $unknownWave6Spells = @($wave6Spells | Where-Object { $_ -notin $requiredWave6SpellIds })
  if ($unknownWave6Spells.Count -gt 0) {
    throw "Wave 6 published an unknown physical elite spell: $($unknownWave6Spells -join ', ')"
  }
  foreach ($spell in $wave6Spells) {
    if ($spell -eq 'arrow_rain') {
      Wait-LogMarker -PreviousLength $beforeWave6 -Pattern 'ELITE_ARROW_RAIN_RELEASE.*player_only=true' -TimeoutSeconds 45 | Out-Null
      Write-Output 'LIVE_WAVE6_ELITE_SPELL_PASS spell=arrow_rain player_only=1'
      continue
    }
    $ability = switch ($spell) {
      'void_burst' { 'VOID_BURST' }
      'web_shot' { 'WEB_SHOT' }
      default { throw "Wave 6 spell has no physical projectile policy: $spell" }
    }
    Wait-LogMarker -PreviousLength $beforeWave6 -Pattern "ELITE_PROJECTILE_RELEASE.*ability=$ability.*player_only=true.*block_damage=false" -TimeoutSeconds 45 | Out-Null
    Write-Output "LIVE_WAVE6_ELITE_SPELL_PASS spell=$spell ability=$ability player_only=1 block_damage=0"
  }
  Write-Output "LIVE_WAVE6_COMBAT_PASS players=6 planned=4 spawned=4 spells=$($wave6Spells -join ',')"
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'

  $beforeMini = Get-LogLength
  # Wave 3's current elite profiles own one physical cast each: the skeleton
  # rains arrows, Endermen launch Rift Bolts, and the converted spider fires web.
  # Keep the live acceptance set tied to EliteAbilityPolicy rather than older
  # boss-style spell names.
  $requiredWave3SpellIds = @('arrow_rain', 'rift_bolt', 'web_shot')
  $beforeWave3 = Get-LogLength
  $response = Invoke-LocalRcon -CommandText 'cmend test wave 3'
  if ($response -match '(?i)refused|missing|event world') {
    throw "Wave 3 physical elite spell probe was refused:`n$response"
  }
  $assignment = Wait-LogMarker -PreviousLength $beforeWave3 `
    -Pattern 'TEST_WAVE_3_SPELL_ASSIGNMENTS.*miniBossSpells=\[[^\]]*\]' -TimeoutSeconds 8
  $assignmentMatch = [Regex]::Match($assignment, 'miniBossSpells=\[(?<spells>[^\]]*)\]')
  if (-not $assignmentMatch.Success) {
    throw "Wave 3 did not publish its assigned elite spell set.`n$assignment"
  }
  $wave3Spells = @([Regex]::Matches($assignmentMatch.Groups['spells'].Value, '[a-z][a-z_]*') |
    ForEach-Object { $_.Value } | Sort-Object -Unique)
  $missingWave3Spells = @($requiredWave3SpellIds | Where-Object { $_ -notin $wave3Spells })
  $unknownWave3Spells = @($wave3Spells | Where-Object { $_ -notin $requiredWave3SpellIds })
  if ($missingWave3Spells.Count -gt 0 -or $unknownWave3Spells.Count -gt 0) {
    throw "Wave 3 elite spell assignments differ from policy. missing=$($missingWave3Spells -join ',') unknown=$($unknownWave3Spells -join ','). $assignment"
  }

  foreach ($spell in $wave3Spells) {
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "MINIBOSS_SPELL_TELEGRAPH.*spell=$spell" -TimeoutSeconds 45 | Out-Null
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "MINIBOSS_SPELL_CAST.*spell=$spell" -TimeoutSeconds 45 | Out-Null
    if ($spell -eq 'arrow_rain') {
      Wait-LogMarker -PreviousLength $beforeMini `
        -Pattern 'ELITE_ARROW_RAIN_RELEASE.*physical_arrows=5.*player_only=true' -TimeoutSeconds 45 | Out-Null
      Wait-LogMarker -PreviousLength $beforeMini `
        -Pattern 'ELITE_ARROW_RAIN_PLAYER_HIT.*target=' -TimeoutSeconds 45 | Out-Null
      Write-Output 'LIVE_WAVE3_ELITE_SPELL_PASS spell=arrow_rain cast=1 physical_release=1 player_impact=1'
      continue
    }

    $ability = $spell.ToUpperInvariant()
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "ELITE_PROJECTILE_RELEASE.*ability=$ability.*player_only=true.*block_damage=false" -TimeoutSeconds 45 | Out-Null
    Wait-LogMarker -PreviousLength $beforeMini `
      -Pattern "ELITE_PROJECTILE_IMPACT.*ability=$ability.*target=[0-9a-f-]{36}.*block=false.*player_only=true" -TimeoutSeconds 45 | Out-Null
    if ($spell -eq 'web_shot') {
      Wait-LogMarker -PreviousLength $beforeMini `
        -Pattern 'ELITE_SPIDER_WEB_HIT.*player=' -TimeoutSeconds 45 | Out-Null
    }
    Write-Output "LIVE_WAVE3_ELITE_SPELL_PASS spell=$spell cast=1 physical_release=1 impact=1"
  }
  $null = Invoke-LocalRcon -CommandText 'cmend wave clear'
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  $mobMatch = [Regex]::Match($status, 'event-mobs=.*?(\d+)')
  if (-not $mobMatch.Success -or [int]$mobMatch.Groups[1].Value -ne 0) {
    throw "Disposable spell-matrix wave cleanup left event mobs:`n$status"
  }
  $matrixLogDelta = Get-NewLogText -PreviousLength $matrixLogStart
  if ($matrixLogDelta -match 'generated an exception') {
    throw "Spell matrix produced a Paper task exception:`n$matrixLogDelta"
  }
  Write-Output "LIVE_SPELL_MATRIX_PASS boss=7 wave3=$($wave3Spells.Count) assigned=$($wave3Spells -join ',') music_phases=24 cleanup=1 current_phases=6"
} finally {
  Stop-SpellMatrixBots
  try { Invoke-LocalRcon -CommandText 'cmend wave clear' | Out-Null } catch { }
  try { Invoke-LocalRcon -CommandText 'cmend boss kill cleanup' | Out-Null } catch { }
  foreach ($name in $spellMatrixBotNames) {
    Remove-Item -LiteralPath (Join-Path $controlDirectory ($name + '.mode')) -Force -ErrorAction SilentlyContinue
  }
}
