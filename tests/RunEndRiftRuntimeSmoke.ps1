[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [int]$RconPort = 25576,
  [string]$LogPath = ''
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $scriptRoot '..\local-runtime\end-rift-server'
}
if ([string]::IsNullOrWhiteSpace($LogPath)) {
  $LogPath = Join-Path $scriptRoot '..\local-runtime\end-rift-paper-rerun.out.log'
}
$ServerDir = (Resolve-Path $ServerDir).Path
$propertiesPath = Join-Path $ServerDir 'server.properties'
$eventConfigPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\config.yml'
if (-not (Test-Path -LiteralPath $propertiesPath) -or -not (Test-Path -LiteralPath $eventConfigPath)) {
  throw 'Runtime smoke requires the isolated End Rift server copy, not the production server.'
}
if ((Get-Content -LiteralPath $eventConfigPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Runtime smoke refused a non-local End Rift configuration.'
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  if ($line -match '^([^#=]+)=(.*)$') {
    $properties[$matches[1].Trim()] = $matches[2].Trim()
  }
}
$rconPassword = $properties['rcon.password']
if ([string]::IsNullOrWhiteSpace($rconPassword)) {
  throw 'Isolated RCON password is missing.'
}

function Read-Exact {
  param([int]$Length)
  $buffer = [byte[]]::new($Length)
  $offset = 0
  while ($offset -lt $Length) {
    $read = $script:stream.Read($buffer, $offset, $Length - $offset)
    if ($read -le 0) { throw 'RCON connection closed unexpectedly.' }
    $offset += $read
  }
  return $buffer
}

function Write-Packet {
  param([int]$Id, [int]$Type, [string]$Body)
  $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
  $payloadLength = 4 + 4 + $bodyBytes.Length + 2
  $packet = [byte[]]::new(4 + $payloadLength)
  [BitConverter]::GetBytes($payloadLength).CopyTo($packet, 0)
  [BitConverter]::GetBytes($Id).CopyTo($packet, 4)
  [BitConverter]::GetBytes($Type).CopyTo($packet, 8)
  $bodyBytes.CopyTo($packet, 12)
  $script:stream.Write($packet, 0, $packet.Length)
}

function Read-Packet {
  $size = [BitConverter]::ToInt32((Read-Exact 4), 0)
  if ($size -lt 10 -or $size -gt 8192) { throw "Invalid RCON packet size: $size" }
  $payload = Read-Exact $size
  [pscustomobject]@{
    Id = [BitConverter]::ToInt32($payload, 0)
    Body = [Text.Encoding]::UTF8.GetString($payload, 8, $size - 10)
  }
}

function Invoke-RconCommand {
  param([string]$CommandText)
  $client = [Net.Sockets.TcpClient]::new()
  try {
    $client.Connect('127.0.0.1', $RconPort)
    $script:stream = $client.GetStream()
    Write-Packet 61001 3 $rconPassword
    $auth = Read-Packet
    if ($auth.Id -eq -1) { throw 'RCON authentication failed.' }
    Write-Packet 61002 2 $CommandText
    return (Read-Packet).Body
  } finally {
    if ($script:stream) { $script:stream.Dispose() }
    $client.Dispose()
    $script:stream = $null
  }
}

function Assert-Text {
  param([string]$Label, [string]$Text, [string]$Expected)
  if ($Text -notmatch [regex]::Escape($Expected)) {
    throw "$Label did not contain '$Expected'. Actual response: $Text"
  }
  Write-Host "PASS $Label"
}

function Assert-NoConfiguredCore {
  param([string]$Text)
  if ($Text -notmatch 'state=.*(?:UNCONFIGURED|UNLOCKED)' -or $Text -notmatch 'requiredPlayers=.*0') {
    throw "fresh state did not have an empty event configuration. Actual response: $Text"
  }
  Write-Host 'PASS fresh state has no configured Core'
}

$plugins = Invoke-RconCommand 'plugins'
Assert-Text 'typed plugins loaded' $plugins 'CopiMineEndEvent'
Assert-Text 'WorldCore loaded' $plugins 'CopiMineWorldCore'
Assert-Text 'Artifacts loaded' $plugins 'CopiMineArtifacts'
$artifactsReload = Invoke-RconCommand 'cmartifacts reload'
Assert-Text 'Artifacts PostgreSQL command path' $artifactsReload 'CopiMineArtifacts catalog reloaded.'

$status = Invoke-RconCommand 'cmend status'
Assert-NoConfiguredCore $status
Assert-Text 'fresh state has no boss' $status 'boss='
if ($status -match 'coreOverlay=true') {
  Assert-Text 'core visual status is exposed' $status 'visuals='
  Assert-Text 'core overlay is present when configured' $status 'coreOverlay=true'
  Assert-Text 'rune overlays are present when configured' $status 'runes='
}

$testWave = Invoke-RconCommand 'cmend test wave 1'
Assert-Text 'test wave refuses missing Core' $testWave 'event world'

$testBoss = Invoke-RconCommand 'cmend boss spawn'
Assert-Text 'test boss refuses missing Core' $testBoss 'Core'

$cleanup = Invoke-RconCommand 'cmend cleanup'
Assert-Text 'cleanup requires confirmation' $cleanup 'confirm'

$cancel = Invoke-RconCommand 'cmend ritual cancel'
Assert-Text 'ritual cancel requires confirmation' $cancel 'confirm'

$reset = Invoke-RconCommand 'cmend resources reset'
Assert-Text 'resource reset requires confirmation' $reset 'confirm'

$unlock = Invoke-RconCommand 'cmend unlock confirm'
Assert-Text 'unlock requires official death' $unlock 'official'

$clientStatus = Invoke-RconCommand 'cmend client status'
Assert-Text 'client bridge status is available' $clientStatus 'channel='

if (Test-Path -LiteralPath $LogPath) {
  $log = (Get-Content -LiteralPath $LogPath -Tail 2500) -join [Environment]::NewLine
  Assert-Text 'End Event services ready' $log 'CopiMineEndEvent services ready'
  Assert-Text 'EconomyCore PostgreSQL ready' $log 'CopiMineEconomyCore PostgreSQL is ready.'
  if ($status -match 'coreOverlay=true') {
    Assert-Text 'physical core/rune visual path' $log 'END_EVENT_PHYSICAL_VISUALS'
  }
  if ($log -match 'NoClassDefFoundError|CopiMineEndEvent failed closed') {
    throw 'End Rift typed dependency or bootstrap failure was found in the local runtime log.'
  }
}

Write-Host 'End Rift isolated runtime smoke passed.'
