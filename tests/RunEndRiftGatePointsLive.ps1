[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftGateProbe',
  [ValidateRange(30, 180)]
  [int]$TimeoutSeconds = 90
)

# Local-only live check. It changes only durable event layout metadata and
# restores it through the plugin before returning the current map to the user.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftGatePointsBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLogDirectory = Join-Path $runtimeRoot 'gate-points-live'
$botLog = Join-Path $botLogDirectory ($BotName + '.out.log')
$botErr = Join-Path $botLogDirectory ($BotName + '.err.log')
$evidencePath = Join-Path $runtimeRoot 'gate-points-live.log'
$process = $null

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "Gate points RCON failed: $CommandText`n$output" }
  return $output.Trim()
}

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

function Assert-LocalOnly {
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') { throw "Gate points runner refuses Git branch '$branch'." }
  $config = Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw
  if ($config -notmatch '(?m)^environment:\s*local\s*$') { throw 'Gate points runner refuses a non-local End Rift configuration.' }
  $properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'Gate points runner refuses non-isolated server ports.'
  }
  if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) { throw "Paper log is missing: $paperLog" }
}

function Wait-Text {
  param([Parameter(Mandatory = $true)][string]$LiteralPath, [Parameter(Mandatory = $true)][string]$Pattern, [int]$WaitSeconds = $TimeoutSeconds)
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Path -LiteralPath $LiteralPath -PathType Leaf) {
      if ((Get-Content -LiteralPath $LiteralPath -Raw) -match $Pattern) { return }
    }
    Start-Sleep -Milliseconds 500
  }
  throw "Gate points probe timed out waiting for '$Pattern'."
}

Assert-LocalOnly
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  [IO.File]::WriteAllText($evidencePath, "GATE_POINTS_START $(Get-Date -Format o) bot=$BotName", [Text.UTF8Encoding]::new($false))

  # Set a disposable Core anchor so the player command is accepted. The
  # current local scene is restored explicitly in finally; this keeps the
  # user's current map and the next smoke test deterministic.
  $null = Invoke-LocalRcon 'cmend gate delete confirm'
  $null = Invoke-LocalRcon 'cmend core remove confirm'
  $null = Invoke-LocalRcon 'cmend core setat 8 68 -39 2'
  $null = Invoke-LocalRcon "op $BotName"
  $null = Invoke-LocalRcon "gamemode creative $BotName"

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $arguments = '"' + $botScript + '" ' + $BotName + ' 30000 8 68 -39'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $botLog -RedirectStandardError $botErr -WindowStyle Hidden -PassThru

  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if ($list -match [Regex]::Escape($BotName)) { break }
    if ($attempt -eq 89) { throw "Gate points bot did not join: $BotName`n$list" }
    Start-Sleep -Milliseconds 500
  }
  # In offline mode Paper cannot resolve a never-seen username before the
  # first login. Re-apply OP after the bot is present so the player-side
  # command reaches the End Event permission guard.
  Start-Sleep -Seconds 5
  $null = Invoke-LocalRcon "op $BotName"
  Start-Sleep -Seconds 2
  $teleport = Invoke-LocalRcon "minecraft:teleport $BotName 8.5 68 -41.5 0 24"
  Write-Evidence "GATE_POINTS_TELEPORT bot=$BotName response=$($teleport.Trim())"
  Wait-Text -LiteralPath $botLog -Pattern 'GATE_POINTS_COMMANDS_SENT' -WaitSeconds $TimeoutSeconds

  $botText = Get-Content -LiteralPath $botLog -Raw
  $firstMatch = [Regex]::Match($botText, 'GATE_TARGET_1\s+\S+\s+(\-?\d+),(\-?\d+),(\-?\d+)')
  $secondMatch = [Regex]::Match($botText, 'GATE_TARGET_2\s+\S+\s+(\-?\d+),(\-?\d+),(\-?\d+)')
  if (-not $firstMatch.Success -or -not $secondMatch.Success) { throw 'Gate points bot did not record both aimed blocks.' }
  $firstText = "$($firstMatch.Groups[1].Value),$($firstMatch.Groups[2].Value),$($firstMatch.Groups[3].Value)"
  $secondText = "$($secondMatch.Groups[1].Value),$($secondMatch.Groups[2].Value),$($secondMatch.Groups[3].Value)"
  $info = Invoke-LocalRcon 'cmend gate info'
  $infoText = ($info -join [Environment]::NewLine) -replace '\u00A7.', ''
  if ($infoText -notmatch [Regex]::Escape("CopiMine $firstText") -or $infoText -notmatch [Regex]::Escape("CopiMine $secondText")) {
    throw "Gate points do not match the blocks under the bot crosshair. targets=$firstText/$secondText info=$infoText"
  }
  Write-Evidence "GATE_POINTS_PASS bot=$BotName pos1=$firstText pos2=$secondText source=server-side-crosshair"
} finally {
  if ($process -and -not $process.HasExited) {
    try { $process.Kill() } catch { }
    try { $process.WaitForExit(5000) | Out-Null } catch { }
  }
  try { $null = Invoke-LocalRcon "deop $BotName" } catch { }
  try { $null = Invoke-LocalRcon 'cmend gate delete confirm' } catch { }
  try { $null = Invoke-LocalRcon 'cmend core remove confirm' } catch { }
  try { $null = Invoke-LocalRcon 'cmend resources reset confirm' } catch { }
  try { $null = Invoke-LocalRcon 'execute in minecraft:overworld run fill 29 68 -40 29 71 -38 minecraft:obsidian' } catch { }
  try { $null = Invoke-LocalRcon 'cmend core setat 8 68 -39 2' } catch { }
  try { $null = Invoke-LocalRcon 'cmend gate setat 29 68 -40 29 71 -38' } catch { }
}
