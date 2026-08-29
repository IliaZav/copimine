[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftMobNow',
  [int]$BotDurationSeconds = 30
)

# This is a local red/green runtime contract. It deliberately uses an
# isolated Paper process and only event-owned test entities.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtime = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtime 'end-rift-server')).Path
$rcon = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftMobCombatBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLog = Join-Path $runtime 'mob-combat-live-bot.log'
$botErr = Join-Path $runtime 'mob-combat-live-bot.err.log'

function Rcon([string]$command) {
  $result = & powershell -NoProfile -ExecutionPolicy Bypass -File $rcon -ServerDir $serverDir -RconPort 25576 -CommandText $command | Out-String
  if ($LASTEXITCODE -ne 0) { throw "RCON failed: $command`n$result" }
  $result.Trim()
}

function Core([string]$status) {
  $match = [Regex]::Match($status, 'core=\S+\s+(-?\d+),(-?\d+),(-?\d+)')
  if (-not $match.Success) { throw "Core coordinates missing:`n$status" }
  @([int]$match.Groups[1].Value, [int]$match.Groups[2].Value, [int]$match.Groups[3].Value)
}

$process = $null
$stdout = $null
$stderr = $null
try {
  if ((Get-Content (Join-Path $root 'copimine-end-event\config.yml') -Raw) -notmatch '(?m)^environment:\s*local\s*$') { throw 'Refused: config is not local.' }
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
  $null = Rcon "effect give $BotName minecraft:resistance 1000 4 true"
  $null = Rcon "tp $BotName $($core[0] + 8) $($core[1] + 1) $($core[2]) 90 0"
  $null = Rcon "give $BotName minecraft:netherite_sword 1"
  Start-Sleep -Seconds 2
  $response = Rcon 'cmend test wave 1'
  if ($response -match '(?i)refused|not created|event world') { throw "Wave spawn refused: $response" }
  Start-Sleep -Seconds ([Math]::Max(10, $BotDurationSeconds - 8))
  if (-not $process.HasExited) { $process.WaitForExit(5000) | Out-Null }
  if ($stdout) { [IO.File]::WriteAllText($botLog, $stdout.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) }
  if ($stderr) { [IO.File]::WriteAllText($botErr, $stderr.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) }
  $botText = if (Test-Path $botLog) { Get-Content $botLog -Raw } else { '' }
  $moved = ([Regex]::Matches($botText, 'MOB_MOVED')).Count
  $hurt = ([Regex]::Matches((Get-Content $paperLog -Raw), 'WAVE_MOB_DAMAGE .*victim=.* cancelled=false')).Count
  $attacks = ([Regex]::Matches($botText, 'PLAYER_ATTACK')).Count
  if ($moved -lt 1) { throw "RED: no live wave mob moved; bot log=$botLog" }
  if ($attacks -lt 1) { throw "RED: player could not reach and attack a live wave mob; bot log=$botLog" }
  if ($hurt -lt 1) { throw "RED: no live wave mob attack reached the player; End Rift log=$paperLog" }
  $paper = Get-Content $paperLog -Raw
  $ai = ([Regex]::Matches($paper, 'WAVE_AI_TARGET')).Count
  $paths = ([Regex]::Matches($paper, 'WAVE_AI_PATH')).Count
  if ($ai -lt 1 -or $paths -lt 1) { throw "Live controller did not report target/path markers: target=$ai path=$paths" }
  Write-Output "LIVE_MOB_COMBAT_PASS moved=$moved attacks=$attacks player_hurt=$hurt ai_targets=$ai ai_paths=$paths"
} finally {
  try { Rcon 'cmend wave clear' | Out-Null } catch { }
  try { Rcon 'cmend boss kill cleanup' | Out-Null } catch { }
  if ($process -and -not $process.HasExited) { try { $process.Kill() } catch { }; $process.WaitForExit(5000) | Out-Null }
  if ($process -and $stdout -and -not (Test-Path $botLog)) { try { [IO.File]::WriteAllText($botLog, $stdout.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { } }
  if ($process -and $stderr -and -not (Test-Path $botErr)) { try { [IO.File]::WriteAllText($botErr, $stderr.GetAwaiter().GetResult(), [Text.UTF8Encoding]::new($false)) } catch { } }
}
