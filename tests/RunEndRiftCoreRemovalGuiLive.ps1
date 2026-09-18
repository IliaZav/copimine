[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftGuiProbe',
  [ValidateRange(30, 300)]
  [int]$TimeoutSeconds = 90
)

# Local-only live GUI regression.  The bot is disposable and the command
# refuses a production config or a non-isolated Paper port before connecting.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftCoreRemovalGuiBot.js'
$paperLog = Join-Path $serverDir 'logs\latest.log'
$botLogDirectory = Join-Path $runtimeRoot 'core-removal-gui'
$botLog = Join-Path $botLogDirectory ($BotName + '.out.log')
$botErr = Join-Path $botLogDirectory ($BotName + '.err.log')
$evidencePath = Join-Path $runtimeRoot 'core-removal-gui-live.log'
$process = $null

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $output = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "GUI removal RCON failed: $CommandText`n$output"
  }
  return $output.Trim()
}

function Write-Evidence {
  param([Parameter(Mandatory = $true)][string]$Text)
  Write-Output $Text
  Add-Content -LiteralPath $evidencePath -Value $Text -Encoding UTF8
}

function Wait-Text {
  param(
    [Parameter(Mandatory = $true)][string]$LiteralPath,
    [Parameter(Mandatory = $true)][string]$Pattern,
    [int]$WaitSeconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Path -LiteralPath $LiteralPath -PathType Leaf) {
      $text = Get-Content -LiteralPath $LiteralPath -Raw
      if ($text -match $Pattern) { return }
    }
    Start-Sleep -Milliseconds 500
  }
  throw "GUI removal probe timed out waiting for '$Pattern'."
}

function Assert-LocalOnly {
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "GUI removal runner refuses Git branch '$branch'."
  }
  $config = Get-Content -LiteralPath (Join-Path $root 'copimine-end-event\config.yml') -Raw
  if ($config -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'GUI removal runner refuses a non-local End Rift configuration.'
  }
  $properties = Get-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Raw
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
      $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'GUI removal runner refuses non-isolated server ports.'
  }
  if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
    throw "Paper log is missing: $paperLog"
  }
}

Assert-LocalOnly
try {
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  [IO.File]::WriteAllText($evidencePath, "CORE_REMOVAL_GUI_START $(Get-Date -Format o) bot=$BotName", [Text.UTF8Encoding]::new($false))

  # Prepare one deterministic local Core without touching world/database data
  # outside the isolated event scene.
  $null = Invoke-LocalRcon 'cmend core remove confirm'
  $null = Invoke-LocalRcon 'cmend core setat 8 68 -39 2'
  $null = Invoke-LocalRcon "op $BotName"
  $null = Invoke-LocalRcon "gamemode creative $BotName"

  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $arguments = '"' + $botScript + '" ' + $BotName + ' 45000 8 68 -39'
  $process = Start-Process -FilePath $node -ArgumentList $arguments -WorkingDirectory $root `
    -RedirectStandardOutput $botLog -RedirectStandardError $botErr -WindowStyle Hidden -PassThru

  for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $list = Invoke-LocalRcon 'list'
    if ($list -match [Regex]::Escape($BotName)) { break }
    if ($attempt -eq 89) { throw "GUI removal bot did not join: $BotName`n$list" }
    Start-Sleep -Milliseconds 500
  }
  # AuthMe can remove OP granted before a disposable offline username has
  # completed its first login.  Re-apply OP after the client is online so the
  # real BlockBreakEvent permission path, including the confirmation GUI, is
  # exercised instead of failing at the command guard.
  Start-Sleep -Seconds 5
  $null = Invoke-LocalRcon "op $BotName"
  Start-Sleep -Seconds 2
  # Keep the disposable client within vanilla block-interaction reach.  The
  # bot still sends the real block_dig packets, but the server rejects a
  # packet from the old 8-block-away probe before BlockBreakEvent can open
  # the operator GUI.
  $teleport = Invoke-LocalRcon "minecraft:teleport $BotName 8.5 68 -41.5 0 0"
  Write-Evidence "CORE_REMOVAL_GUI_TELEPORT bot=$BotName response=$($teleport.Trim())"
  Write-Evidence "CORE_REMOVAL_GUI_PLAYER_ONLINE bot=$BotName"
  Wait-Text -LiteralPath $botLog -Pattern 'CORE_REMOVAL_GUI_OPEN' -WaitSeconds $TimeoutSeconds
  Wait-Text -LiteralPath $botLog -Pattern 'CORE_REMOVAL_GUI_CLICK.*slot=11' -WaitSeconds 20

  $deadline = (Get-Date).AddSeconds(20)
  $status = ''
  while ((Get-Date) -lt $deadline) {
    $status = Invoke-LocalRcon 'cmend status'
    $statusText = ($status -join [Environment]::NewLine) -replace '\u00A7.', ''
    if ([Regex]::IsMatch($statusText, 'state=.*UNCONFIGURED') -and
        [Regex]::IsMatch($statusText, 'coreOverlay=false') -and
        [Regex]::IsMatch($statusText, 'runes=0/0')) { break }
    Start-Sleep -Milliseconds 500
  }
  $statusText = ($status -join [Environment]::NewLine) -replace '\u00A7.', ''
  if ($statusText -notmatch 'state=.*UNCONFIGURED' -or
      $statusText -notmatch 'coreOverlay=false' -or
      $statusText -notmatch 'runes=0/0') {
    throw "Core removal GUI did not clear the event visuals/state:`n$status"
  }
  $statePath = Join-Path $serverDir 'plugins\CopiMineEndEvent\event-state.yml'
  if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    throw "Event state is missing while reading the preserved Core block: $statePath"
  }
  $stateText = Get-Content -LiteralPath $statePath -Raw
  $blockDataMatch = [Regex]::Match($stateText, '(?m)^\s*block-data:\s*(\S+)\s*$')
  if (-not $blockDataMatch.Success) {
    throw 'Persisted Core block-data is missing; refusing to guess a replacement block.'
  }
  $expectedBlockData = $blockDataMatch.Groups[1].Value
  $expectedMaterial = ($expectedBlockData -split '\[')[0]
  $restored = Invoke-LocalRcon "execute in minecraft:overworld if block 8 68 -39 $expectedMaterial run minecraft:time query gametime"
  if ($restored -notmatch 'The time is') {
    throw "Core removal GUI did not restore the original vanilla block:`n$restored"
  }
  foreach ($displayType in @('item_display', 'block_display', 'text_display')) {
    $remaining = Invoke-LocalRcon "execute in minecraft:overworld positioned 8.5 69 -38.5 if entity @e[type=minecraft:$displayType,distance=..2] run minecraft:time query gametime"
    if (-not [string]::IsNullOrWhiteSpace($remaining) -and $remaining -match 'The time is') {
      throw "Core removal GUI left a $displayType near the Core: $remaining"
    }
  }
  Write-Evidence "CORE_REMOVAL_GUI_PASS bot=$BotName state=UNCONFIGURED coreOverlay=false runes=0/0 restored_block=$expectedBlockData displays=0"
} finally {
  if ($process -and -not $process.HasExited) {
    try { $process.Kill() } catch { }
    try { $process.WaitForExit(5000) | Out-Null } catch { }
  }
  try { $null = Invoke-LocalRcon "deop $BotName" } catch { }
  try { $null = Invoke-LocalRcon 'cmend core remove confirm' } catch { }
  try { $null = Invoke-LocalRcon 'cmend resources reset confirm' } catch { }
  try { $null = Invoke-LocalRcon 'execute in minecraft:overworld run fill 29 68 -40 29 71 -38 minecraft:obsidian' } catch { }
  try { $null = Invoke-LocalRcon 'cmend core setat 8 68 -39 2' } catch { }
  try { $null = Invoke-LocalRcon 'cmend gate setat 29 68 -40 29 71 -38' } catch { }
}
