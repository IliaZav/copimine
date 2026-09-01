[CmdletBinding()]
param(
  [ValidateRange(16, 20)]
  [int]$PlayerCount = 20,
  [ValidateRange(20, 300)]
  [int]$BotDurationSeconds = 180,
  [ValidateRange(45, 180)]
  [int]$TimeoutSeconds = 90
)

# Local-only load/smoke probe.  It uses disposable real protocol clients and
# the test boss harness; it never rebuilds a world, changes production data,
# or connects to anything except the isolated local Paper/RCON ports.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftObeliskBot.js'
$localRconPort = 25576
$paperLog = Join-Path $serverDir 'logs\latest.log'
$configPath = Join-Path $root 'copimine-end-event\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$botLogDirectory = Join-Path $runtimeRoot 'rift-obelisk-load-bots'
$playerNames = @(1..$PlayerCount | ForEach-Object { 'ObeliskLoad{0:D2}' -f $_ })

function Assert-LocalOnly {
  $config = Get-Content -LiteralPath $configPath -Raw
  $properties = Get-Content -LiteralPath $propertiesPath -Raw
  if ($config -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'Obelisk load probe refused a non-local End Rift configuration.'
  }
  if ($properties -notmatch '(?m)^server-port=25566\s*$' -or
      $properties -notmatch '(?m)^rcon\.port=25576\s*$') {
    throw 'Obelisk load probe requires isolated 25566/25576 ports.'
  }
  $branch = (& git -C $root branch --show-current 2>$null).Trim()
  if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
    throw "Obelisk load probe refused Git branch '$branch'."
  }
  if (-not (Test-Path -LiteralPath $botScript -PathType Leaf)) {
    throw "Local obelisk bot is missing: $botScript"
  }
}

$script:localRconSession = $null
$script:localRconRequestId = 64000

function Read-RconExact {
  param(
    [Parameter(Mandatory = $true)][Net.Sockets.NetworkStream]$Stream,
    [Parameter(Mandatory = $true)][int]$Length
  )
  $buffer = [byte[]]::new($Length)
  $offset = 0
  while ($offset -lt $Length) {
    $read = $Stream.Read($buffer, $offset, $Length - $offset)
    if ($read -le 0) { throw 'Local RCON connection closed while reading a response.' }
    $offset += $read
  }
  return $buffer
}

function Write-RconPacket {
  param(
    [Parameter(Mandatory = $true)][Net.Sockets.NetworkStream]$Stream,
    [Parameter(Mandatory = $true)][int]$Id,
    [Parameter(Mandatory = $true)][int]$Type,
    [Parameter(Mandatory = $true)][string]$Body
  )
  $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
  $payloadLength = 4 + 4 + $bodyBytes.Length + 2
  $packet = [byte[]]::new(4 + $payloadLength)
  [BitConverter]::GetBytes($payloadLength).CopyTo($packet, 0)
  [BitConverter]::GetBytes($Id).CopyTo($packet, 4)
  [BitConverter]::GetBytes($Type).CopyTo($packet, 8)
  $bodyBytes.CopyTo($packet, 12)
  $Stream.Write($packet, 0, $packet.Length)
  $Stream.Flush()
}

function Read-RconPacket {
  param([Parameter(Mandatory = $true)][Net.Sockets.NetworkStream]$Stream)
  $size = [BitConverter]::ToInt32((Read-RconExact -Stream $Stream -Length 4), 0)
  if ($size -lt 10 -or $size -gt 8192) {
    throw "Invalid local RCON packet size: $size"
  }
  $payload = Read-RconExact -Stream $Stream -Length $size
  return [pscustomobject]@{
    Id = [BitConverter]::ToInt32($payload, 0)
    Body = [Text.Encoding]::UTF8.GetString($payload, 8, $size - 10)
  }
}

function Open-LocalRconSession {
  $rconPassword = ''
  foreach ($line in [IO.File]::ReadLines($propertiesPath)) {
    if ($line -match '^rcon\.password=(.*)$') {
      $rconPassword = [string]$matches[1].Trim()
      break
    }
  }
  if ([string]::IsNullOrWhiteSpace($rconPassword)) {
    throw 'Local RCON password is missing.'
  }
  $client = [Net.Sockets.TcpClient]::new()
  try {
    $client.NoDelay = $true
    $client.Connect('127.0.0.1', $localRconPort)
    $stream = $client.GetStream()
    $stream.ReadTimeout = 30000
    Write-RconPacket -Stream $stream -Id 64001 -Type 3 -Body $rconPassword
    $auth = Read-RconPacket -Stream $stream
    if ($auth.Id -eq -1) { throw 'Local RCON authentication failed.' }
    return [pscustomobject]@{ Client = $client; Stream = $stream }
  } catch {
    $client.Dispose()
    throw
  }
}

function Close-LocalRconSession {
  if ($null -eq $script:localRconSession) { return }
  try { $script:localRconSession.Stream.Dispose() } catch { }
  try { $script:localRconSession.Client.Dispose() } catch { }
  $script:localRconSession = $null
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  if ($null -eq $script:localRconSession) {
    $script:localRconSession = Open-LocalRconSession
  }
  $script:localRconRequestId++
  $requestId = $script:localRconRequestId
  Write-RconPacket -Stream $script:localRconSession.Stream -Id $requestId -Type 2 -Body $CommandText
  do {
    $response = Read-RconPacket -Stream $script:localRconSession.Stream
  } while ($response.Id -ne $requestId)
  return $response.Body.Trim()
}

function Read-SharedText {
  param([Parameter(Mandatory = $true)][string]$Path)
  $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  try {
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  } finally {
    $stream.Dispose()
  }
}

function Get-LogLength {
  # ReadSharedText/Substring use character offsets.  Get-Item.Length is a
  # byte count for UTF-8 logs and would skip past fresh markers as soon as a
  # Cyrillic line appears before the probe starts.
  return (Read-SharedText -Path $paperLog).Length
}

function Strip-MinecraftColors {
  param([Parameter(Mandatory = $true)][string]$Text)
  return ($Text -replace '§[0-9A-FK-ORa-fk-or]', '')
}

function Wait-LogCount {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [int]$Minimum = 1,
    [int64]$AfterOffset = 0,
    [int]$WaitSeconds = 30
  )
  $deadline = (Get-Date).AddSeconds($WaitSeconds)
  do {
    $text = Read-SharedText -Path $paperLog
    $tail = if ($AfterOffset -lt $text.Length) { $text.Substring([int]$AfterOffset) } else { '' }
    $matches = @($tail -split '\r?\n' | Where-Object { $_ -match $Pattern })
    if ($matches.Count -ge $Minimum) { return $matches }
    Start-Sleep -Milliseconds 250
  } while ((Get-Date) -lt $deadline)
  throw "Timed out waiting for $Minimum log matches of '$Pattern'. See $paperLog"
}

function Wait-Players {
  $deadline = (Get-Date).AddSeconds(90)
  do {
    $list = Invoke-LocalRcon -CommandText 'list'
    $missing = @($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) })
    if ($missing.Count -eq 0) { return }
    Start-Sleep -Milliseconds 500
  } while ((Get-Date) -lt $deadline)
  throw "Obelisk load clients did not all join: $($missing -join ', ')"
}

function Get-PlayerPosition {
  param([Parameter(Mandatory = $true)][string]$Name)
  $data = Invoke-LocalRcon -CommandText ("data get entity $Name Pos")
  $match = [Regex]::Match($data,
    '\[\s*([-0-9.]+)d,\s*([-0-9.]+)d,\s*([-0-9.]+)d\s*\]')
  if (-not $match.Success) { return $null }
  return [pscustomobject]@{
    X = [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture)
    Y = [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture)
    Z = [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  }
}

function Test-LoadArenaPosition {
  param([object]$Position)
  if ($null -eq $Position) { return $false }
  $blockX = [Math]::Floor($Position.X)
  $blockY = [Math]::Floor($Position.Y)
  $blockZ = [Math]::Floor($Position.Z)
  return $blockX -ge -12 -and $blockX -le 28 `
    -and $blockY -ge 65 -and $blockY -le 71 `
    -and $blockZ -ge -59 -and $blockZ -le -19
}

function Start-LoadBot {
  param([Parameter(Mandatory = $true)][string]$Name)
  New-Item -ItemType Directory -Path $botLogDirectory -Force | Out-Null
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = (Get-Command node.exe -ErrorAction Stop).Source
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $durationMs = $BotDurationSeconds * 1000
  if ($null -ne $startInfo.ArgumentList) {
    $startInfo.ArgumentList.Add($botScript)
    $startInfo.ArgumentList.Add($Name)
    $startInfo.ArgumentList.Add(([string]$durationMs))
  } else {
    $startInfo.Arguments = '"' + $botScript + '" ' + $Name + ' ' + ([string]$durationMs)
  }
  $startInfo.EnvironmentVariables['END_RIFT_BOT_HOST'] = '127.0.0.1'
  $startInfo.EnvironmentVariables['END_RIFT_BOT_PORT'] = '25566'
  $startInfo.EnvironmentVariables['END_RIFT_REFLECT_ENABLED'] = '0'
  # AuthMe is exercised through the local console force-login below.  Sending
  # twenty concurrent register/login command streams makes AuthMe's own
  # authentication timeout the bottleneck and can eject a real protocol client
  # before the event load has even started.
  $startInfo.EnvironmentVariables['END_RIFT_SKIP_AUTH_CHAT'] = '1'
  $startInfo.EnvironmentVariables['END_RIFT_OBELISK_X'] = '15.5'
  $startInfo.EnvironmentVariables['END_RIFT_OBELISK_Y'] = '68'
  $startInfo.EnvironmentVariables['END_RIFT_OBELISK_Z'] = '-39.5'
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process.Start() | Out-Null
  return [pscustomobject]@{
    Name = $Name
    Process = $process
    OutputTask = $process.StandardOutput.ReadToEndAsync()
    ErrorTask = $process.StandardError.ReadToEndAsync()
  }
}

function Save-BotLogs {
  param([object[]]$Processes)
  for ($index = 0; $index -lt $Processes.Count; $index++) {
    $entry = $Processes[$index]
    $output = $entry.OutputTask.GetAwaiter().GetResult()
    $errorOutput = $entry.ErrorTask.GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $botLogDirectory ($entry.Name + '.log')),
      $output, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $botLogDirectory ($entry.Name + '.err.log')),
      $errorOutput, [Text.UTF8Encoding]::new($false))
  }
}

Assert-LocalOnly
if (-not (Test-Path -LiteralPath $paperLog -PathType Leaf)) {
  throw "Local Paper log is missing: $paperLog"
}

$processes = @()
$bossUuid = ''
$success = $false
try {
  $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
  foreach ($name in $playerNames) {
    $processes += Start-LoadBot -Name $name
    # AuthMe performs a database-backed account lookup on each login.  A
    # small deterministic launch gap keeps 20 local clients from competing
    # for the same login timeout and is still a real multiplayer load.
    Start-Sleep -Milliseconds 750
  }
  Wait-Players
  foreach ($name in $playerNames) {
    # This is an event load probe, not an AuthMe benchmark.  Force-login is a
    # local console-only operation on the disposable test accounts, ensuring
    # every real protocol client can remain a gameplay participant while the
    # event is exercised.
    $forceLogin = Invoke-LocalRcon -CommandText ("authme forcelogin $name")
    if ($forceLogin -notmatch 'Force login for') {
      throw "Local AuthMe force-login failed for $name`n$forceLogin"
    }
  }
  Start-Sleep -Seconds 3
  Start-Sleep -Seconds 4
  $destinations = @{}
  for ($index = 0; $index -lt $playerNames.Count; $index++) {
    $name = $playerNames[$index]
    $angle = (2.0D * [Math]::PI * $index) / $playerNames.Count
    $x = 8.5D + [Math]::Cos($angle) * 12.0D
    $z = -38.5D + [Math]::Sin($angle) * 12.0D
    $destinations[$name] = [pscustomobject]@{ X = $x; Z = $z }
    $null = Invoke-LocalRcon -CommandText ("gamemode survival $name")
    $null = Invoke-LocalRcon -CommandText ("attribute $name minecraft:generic.max_health base set 1000")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:resistance 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("effect give $name minecraft:regeneration 1000 4 true")
    $null = Invoke-LocalRcon -CommandText ("minecraft:tp $name " +
      $x.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 68 ' +
      $z.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 0 0')
  }

  # Essentials/AuthMe can briefly apply a position correction while a large
  # batch of clients finishes joining.  Verify the server-side coordinates,
  # then retry only clients that are not actually inside the configured arena;
  # boss scaling must never be inferred from /list alone.
  Start-Sleep -Seconds 2
  $outside = @()
  foreach ($name in $playerNames) {
    if (-not (Test-LoadArenaPosition (Get-PlayerPosition -Name $name))) {
      $outside += $name
    }
  }
  foreach ($name in $outside) {
    $destination = $destinations[$name]
    $null = Invoke-LocalRcon -CommandText ("minecraft:tp $name " +
      $destination.X.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 68 ' +
      $destination.Z.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture) + ' 0 0')
  }
  if ($outside.Count -gt 0) { Start-Sleep -Seconds 2 }
  $stillOutside = @()
  foreach ($name in $playerNames) {
    if (-not (Test-LoadArenaPosition (Get-PlayerPosition -Name $name))) {
      $stillOutside += $name
    }
  }
  if ($stillOutside.Count -gt 0) {
    throw "20-player load clients are not inside the arena after verified teleport: $($stillOutside -join ', ')"
  }

  $spawnOffset = Get-LogLength
  $null = Invoke-LocalRcon -CommandText 'cmend boss spawn'
  $spawnLog = Wait-LogCount -Pattern 'BOSS_STATS .*vanilla_base=' -AfterOffset $spawnOffset -WaitSeconds 30
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  $statusPlain = Strip-MinecraftColors -Text $status
  $bossMatch = [Regex]::Match($status,
    'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\s+hp=([0-9.]+)/([0-9.]+)')
  if (-not $bossMatch.Success) {
    $bossMatch = [Regex]::Match($statusPlain,
      'boss=.*?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\s+hp=([0-9.]+)/([0-9.]+)')
  }
  if (-not $bossMatch.Success) { throw "20-player load boss is missing:`n$status" }
  $bossUuid = $bossMatch.Groups[1].Value
  $maxHealth = [double]::Parse($bossMatch.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  if ($maxHealth -lt 19999.0D) {
    throw "20-player load boss did not receive the 20000 virtual HP cap: max=$maxHealth`n$status"
  }
  $null = Invoke-LocalRcon -CommandText 'cmend boss freeze'
  $halfDamage = ($maxHealth / 2.0D).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
  $spellOffset = Get-LogLength
  $null = Invoke-LocalRcon -CommandText ("cmend boss damage $halfDamage")
  $null = Invoke-LocalRcon -CommandText 'cmend boss phase normal'
  $null = Invoke-LocalRcon -CommandText 'cmend boss spell rift_obelisks'
  Wait-LogCount -Pattern ('RIFT_OBELISKS_SPAWNED .*count=4 .*participants=' + $PlayerCount + ' .*stage=DISTORTION') `
    -AfterOffset $spellOffset -WaitSeconds 30 | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_ACTIVE ' -Minimum 4 -AfterOffset $spellOffset -WaitSeconds 30 | Out-Null
  Wait-LogCount -Pattern 'RIFT_OBELISK_PULSE ' -Minimum 4 -AfterOffset $spellOffset -WaitSeconds 30 | Out-Null
  Wait-LogCount -Pattern 'RIFT_FIREBALL_LAUNCH .*max_active=8' -Minimum 1 -AfterOffset $spellOffset -WaitSeconds 30 | Out-Null

  Start-Sleep -Seconds 3
  $status = Invoke-LocalRcon -CommandText 'cmend status'
  $statusPlain = Strip-MinecraftColors -Text $status
  # Keep the parser tolerant of Bukkit color codes even if a proxy/RCON
  # wrapper returns the raw formatted line.  The counters remain anchored to
  # the two labels, so a stray digit elsewhere cannot satisfy the check.
  $runtimeMatch = [Regex]::Match($status,
    'rift-obelisks=.*?(\d+)/4\s+.*?rift-fireballs=.*?(\d+)')
  if (-not $runtimeMatch.Success) {
    $runtimeMatch = [Regex]::Match($statusPlain,
      'rift-obelisks=(\d+)/4\s+rift-fireballs=(\d+)')
  }
  if (-not $runtimeMatch.Success) { throw "Obelisk load status is missing runtime counters:`n$status" }
  $liveObelisks = [int]$runtimeMatch.Groups[1].Value
  $liveFireballs = [int]$runtimeMatch.Groups[2].Value
  if ($liveObelisks -ne 4) {
    throw "20-player load lost an obelisk without a reflected hit: live=$liveObelisks`n$status"
  }
  if ($liveFireballs -gt 8) {
    throw "Rift Fireball hard cap exceeded: live=$liveFireballs`n$status"
  }
  $list = Invoke-LocalRcon -CommandText 'list'
  $missing = @($playerNames | Where-Object { $list -notmatch [Regex]::Escape($_) })
  if ($missing.Count -gt 0) { throw "20-player load disconnected clients: $($missing -join ', ')`n$list" }
  $success = $true
  Write-Output ("LIVE_RIFT_OBELISK_LOAD_PASS players={0} max_boss_virtual_hp={1} obelisks={2}/4 fireballs={3}/8 staggered=true pulse_radius=5 pulse_ticks=40" -f
    $PlayerCount, $maxHealth, $liveObelisks, $liveFireballs)
} finally {
  foreach ($entry in $processes) {
    if ($entry -and -not $entry.Process.HasExited) {
      try { $entry.Process.Kill() } catch { }
    }
  }
  foreach ($entry in $processes) {
    if ($entry) { try { $entry.Process.WaitForExit(5000) | Out-Null } catch { } }
  }
  if ($processes.Count -gt 0) {
    try { Save-BotLogs -Processes $processes } catch { Write-Warning "Could not save obelisk load bot logs: $($_.Exception.Message)" }
  }
  try {
    $null = Invoke-LocalRcon -CommandText 'cmend boss kill cleanup'
    $cleanup = Invoke-LocalRcon -CommandText 'cmend status'
    $cleanupOk = ($cleanup -match 'boss=.*?none') -and
      ($cleanup -match 'rift-obelisks=.*?0/4') -and
      ($cleanup -match 'rift-fireballs=.*?0')
    if (-not $cleanupOk) {
      throw "Obelisk load cleanup left runtime state:`n$cleanup"
    }
    Write-Output 'LIVE_RIFT_OBELISK_LOAD_CLEANUP_PASS boss=none rift-obelisks=0/4 rift-fireballs=0'
  } catch {
    Write-Warning "Local obelisk load cleanup failed: $($_.Exception.Message)"
    if ($success) { throw }
  } finally {
    Close-LocalRconSession
  }
}
