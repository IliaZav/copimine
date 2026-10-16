[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftMobNow',
  [int]$BotDurationSeconds = 30,
  [string]$ExpectedBranch = 'codex/end-rift-26-3-integrated-final'
)

# This is a local red/green runtime contract. It deliberately uses an
# isolated Paper process and only event-owned test entities.
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
$runtime = Assert-EndRiftLocalRuntimePath `
  -Path $localRuntimeRoot -LocalRuntimeRoot $localRuntimeRoot -MustExist
$serverDir = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtime 'end-rift-server') -LocalRuntimeRoot $runtime -MustExist
$rcon = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLog = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtime 'mob-combat-live-bot.log') -LocalRuntimeRoot $runtime
$botErr = Assert-EndRiftLocalRuntimePath `
  -Path (Join-Path $runtime 'mob-combat-live-bot.err.log') -LocalRuntimeRoot $runtime

$config = Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw
if ($config -notmatch '(?m)^environment:\s*local\s*$' -or
    $config -notmatch '(?m)^\s*schema-version:\s*4\s*$') {
  throw 'Refused: config is not the current local schema.'
}
Assert-EndRiftLocalServerTreeIsolated -ServerDir $serverDir -LocalRuntimeRoot $runtime | Out-Null
Assert-EndRiftLocalServerBind -ServerDir $serverDir -ServerPort 25566 -RconPort 25576 | Out-Null

function Rcon([string]$command) {
  $result = & powershell -NoProfile -ExecutionPolicy Bypass -File $rcon -ServerDir $serverDir -RconPort 25576 -CommandText $command | Out-String
  if ($LASTEXITCODE -ne 0) { throw "RCON failed: $command`n$result" }
  $result.Trim()
}

function ReadAppendedText([string]$Path, [long]$Offset) {
  $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    $start = [Math]::Min([Math]::Max(0L, $Offset), $stream.Length)
    [void]$stream.Seek($start, [IO.SeekOrigin]::Begin)
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 4096, $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally {
    $stream.Dispose()
  }
}

function Core([string]$status) {
  $match = [Regex]::Match($status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core coordinates missing:`n$status" }
  @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

$process = $null
$stdout = $null
$stderr = $null
$null = Assert-EndRiftLocalAuthMeDisabled -PluginListOutput (Rcon 'plugins')
try {
  $null = Rcon 'cmend wave clear'
  $null = Rcon 'cmend boss kill cleanup'
  $core = Core (Rcon 'cmend status')

  $start = [Diagnostics.ProcessStartInfo]::new()
  $start.FileName = (Get-Command node.exe -ErrorAction Stop).Source
  $start.WorkingDirectory = $root
  $start.UseShellExecute = $false
  $start.CreateNoWindow = $true
  $start.RedirectStandardOutput = $true
  $start.RedirectStandardError = $true
  Set-EndRiftLocalBotChildEnvironment -StartInfo $start -BotHost '127.0.0.1' -BotPort 25566
  if ($null -ne $start.ArgumentList) {
    $start.ArgumentList.Add($botScript); $start.ArgumentList.Add($BotName); $start.ArgumentList.Add(([string]($BotDurationSeconds * 1000)))
  } else { $start.Arguments = '"' + $botScript + '" ' + $BotName + ' ' + ([string]($BotDurationSeconds * 1000)) }
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $start; $process.Start() | Out-Null
  $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
  for ($i = 0; $i -lt 40; $i++) {
    if ((Rcon 'list') -match [Regex]::Escape($BotName)) { break }
    Start-Sleep -Milliseconds 500
    if ($i -eq 39) { throw "Bot did not join: $BotName" }
  }
  $null = Rcon "gamemode survival $BotName"
  $null = Rcon "attribute $BotName minecraft:generic.max_health base set 1000"
  $null = Rcon "heal $BotName"
  $null = Rcon "effect clear $BotName minecraft:resistance"
  $null = Rcon "tp $BotName $($core[0] + 8) $($core[1] + 1) $($core[2]) 90 0"
  $null = Rcon "give $BotName minecraft:netherite_sword 1"
  Start-Sleep -Seconds 2
  $paperLogOffset = (Get-Item -LiteralPath $paperLog).Length
  $response = Rcon 'cmend test wave 1'
  if ($response -match '(?i)refused|not created|event world') { throw "Wave spawn refused: $response" }
  Start-Sleep -Seconds ([Math]::Max(10, $BotDurationSeconds - 8))
  if (-not $process.HasExited) { $process.WaitForExit(5000) | Out-Null }
  if ($stdout) { [IO.File]::WriteAllText($botLog, $stdout.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) }
  if ($stderr) { [IO.File]::WriteAllText($botErr, $stderr.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) }
  $botText = if (Test-Path $botLog) { Get-Content $botLog -Raw } else { '' }
  $moved = ([Regex]::Matches($botText, 'MOB_MOVED')).Count
  $paper = ReadAppendedText $paperLog $paperLogOffset
  $playerAttackAttempts = ([Regex]::Matches($botText, 'PLAYER_ATTACK')).Count
  $playerMobDamageEvents = ([Regex]::Matches($paper, 'WAVE_MOB_PLAYER_DAMAGE event=')).Count
  $acceptedPlayerMobHits = ([Regex]::Matches($paper, 'WAVE_MOB_PLAYER_DAMAGE_APPLIED event=.*accepted_before=true')).Count
  $positiveMobDamageDeltas = @([Regex]::Matches($paper, 'WAVE_MOB_PLAYER_DAMAGE_APPLIED event=.*accepted_before=true .*delta=([0-9]+(?:\.[0-9]+)?)') |
    Where-Object { [double]::Parse($_.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture) -gt 0.0D }).Count
  $incomingMobHitEvents = ([Regex]::Matches($paper, 'WAVE_MOB_DAMAGE event=')).Count
  $acceptedIncomingMobHitEvents = ([Regex]::Matches($paper, 'WAVE_MOB_DAMAGE event=.*cancelled=false')).Count
  $positiveIncomingMobDamageEvents = @([Regex]::Matches($paper, 'WAVE_MOB_DAMAGE event=.*final=([0-9]+(?:\.[0-9]+)?) cancelled=false') |
    Where-Object { [double]::Parse($_.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture) -gt 0.0D }).Count
  $positivePlayerHealthTransitions = @([Regex]::Matches($botText, 'PLAYER_HURT .*before=([0-9]+(?:\.[0-9]+)?)\s+after=([0-9]+(?:\.[0-9]+)?)') |
    Where-Object {
      [double]::Parse($_.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture) -gt
        [double]::Parse($_.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
    }).Count
  if ($moved -lt 1) { throw "RED: no live wave mob moved; bot log=$botLog" }
  if ($playerAttackAttempts -lt 1) { throw "RED: player could not reach and attack a live wave mob; bot log=$botLog" }
  if ($playerMobDamageEvents -lt 1) { throw "RED: no player-to-wave-mob damage event was logged; End Rift log=$paperLog" }
  if ($acceptedPlayerMobHits -lt 1) { throw "RED: no player-to-wave-mob hit was accepted; End Rift log=$paperLog" }
  if ($positiveMobDamageDeltas -lt 1) {
    throw "RED: no player hit produced measurable wave-mob health loss; End Rift log=$paperLog"
  }
  if ($incomingMobHitEvents -lt 1) { throw "RED: no wave-mob attack reached the player; End Rift log=$paperLog" }
  if ($acceptedIncomingMobHitEvents -lt 1) { throw "RED: no wave-mob attack was accepted by Paper; End Rift log=$paperLog" }
  if ($positiveIncomingMobDamageEvents -lt 1) { throw "RED: no positive incoming wave-mob damage event was logged; End Rift log=$paperLog" }
  if ($positivePlayerHealthTransitions -lt 1) { throw "RED: player health did not decrease after a wave-mob hit; bot log=$botLog" }
  $ai = ([Regex]::Matches($paper, 'WAVE_AI_TARGET')).Count
  $paths = ([Regex]::Matches($paper, 'WAVE_AI_PATH')).Count
  if ($ai -lt 1 -or $paths -lt 1) { throw "Live controller did not report target/path markers: target=$ai path=$paths" }
  Write-Output "LIVE_MOB_COMBAT_PASS moved=$moved attacks_attempted=$playerAttackAttempts player_mob_hit_events=$playerMobDamageEvents player_mob_hits_accepted=$acceptedPlayerMobHits positive_mob_damage_deltas=$positiveMobDamageDeltas incoming_mob_hit_events=$incomingMobHitEvents incoming_mob_hits_accepted=$acceptedIncomingMobHitEvents positive_incoming_mob_damage_events=$positiveIncomingMobDamageEvents positive_player_health_transitions=$positivePlayerHealthTransitions ai_targets=$ai ai_paths=$paths"
} finally {
  try { Rcon 'cmend wave clear' | Out-Null } catch { }
  try { Rcon 'cmend boss kill cleanup' | Out-Null } catch { }
  if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { }; $process.WaitForExit(5000) | Out-Null }
  if ($process -and $stdout -and -not (Test-Path $botLog)) { try { [IO.File]::WriteAllText($botLog, $stdout.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { } }
  if ($process -and $stderr -and -not (Test-Path $botErr)) { try { [IO.File]::WriteAllText($botErr, $stderr.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { } }
}
