[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [int]$RconPort = 25576
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $repoRoot 'local-runtime')).Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $localRuntimeRoot 'end-rift-server'
}
$ServerDir = (Resolve-Path $ServerDir).Path
$localPrefix = $localRuntimeRoot.TrimEnd('\') + '\'
if (-not $ServerDir.StartsWith($localPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Local scene setup refuses a server outside this worktree local-runtime directory.'
}
$propertiesPath = Join-Path $ServerDir 'server.properties'
$configPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\config.yml'
if (-not (Test-Path -LiteralPath $propertiesPath) -or -not (Test-Path -LiteralPath $configPath)) {
  throw 'Local scene setup requires the isolated End Rift server files.'
}
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Local scene setup refused a non-local End Rift configuration.'
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  if ($line -match '^([^#=]+)=(.*)$') {
    $properties[$matches[1].Trim()] = $matches[2].Trim()
  }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576') {
  throw 'Local scene setup requires server-port=25566 and rcon.port=25576.'
}
$rconPassword = $properties['rcon.password']
if ([string]::IsNullOrWhiteSpace($rconPassword)) {
  throw 'Local scene setup requires a local RCON password.'
}

function Read-Exact {
  param([int]$Length)
  $buffer = [byte[]]::new($Length)
  $offset = 0
  while ($offset -lt $Length) {
    $read = $script:stream.Read($buffer, $offset, $Length - $offset)
    if ($read -le 0) { throw 'Local scene setup RCON connection closed.' }
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
  if ($size -lt 10 -or $size -gt 8192) { throw "Invalid local scene RCON packet size: $size" }
  $payload = Read-Exact $size
  [pscustomobject]@{
    Id = [BitConverter]::ToInt32($payload, 0)
    Body = [Text.Encoding]::UTF8.GetString($payload, 8, $size - 10)
  }
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $client = [Net.Sockets.TcpClient]::new()
  try {
    $client.Connect('127.0.0.1', $RconPort)
    $script:stream = $client.GetStream()
    Write-Packet 63001 3 $rconPassword
    $auth = Read-Packet
    if ($auth.Id -eq -1) { throw 'Local scene setup RCON authentication failed.' }
    Write-Packet 63002 2 $CommandText
    return (Read-Packet).Body
  } finally {
    if ($script:stream) { $script:stream.Dispose() }
    $client.Dispose()
    $script:stream = $null
  }
}

function Invoke-SceneCommand {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = Invoke-LocalRcon $CommandText
  Write-Host "RCON> $CommandText"
  if (-not [string]::IsNullOrWhiteSpace($result)) { Write-Host $result.Trim() }
  return $result
}

function Assert-SceneCondition {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$CommandText
  )
  $result = Invoke-SceneCommand $CommandText
  if (($result -join [Environment]::NewLine) -notmatch 'The time is') {
    throw "Local scene condition failed: $Label. Actual response: $result"
  }
  Write-Host "PASS $Label"
}

function Assert-SceneCommandText {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$CommandText,
    [Parameter(Mandatory = $true)][string]$Expected
  )
  $result = Invoke-SceneCommand $CommandText
  if (($result -join [Environment]::NewLine) -notmatch [regex]::Escape($Expected)) {
    throw "Local scene command failed: $Label. Expected '$Expected'. Actual response: $result"
  }
  Write-Host "PASS $Label"
}

# CopiMine is the primary overworld; Minecraft's /execute dimension argument
# uses the dimension key rather than Bukkit's custom world name.
$world = 'minecraft:overworld'
# The saved local End Rift Core is at 8,68,-39.  These bounds are the existing
# 20 horizontal / 3 vertical event bounds, rebuilt from local stone only.
$arenaMinX = -12; $arenaMaxX = 28
$arenaMinY = 65; $arenaMaxY = 71
$arenaMinZ = -59; $arenaMaxZ = -19
$coreX = 8; $coreY = 68; $coreZ = -39
$floorY = $coreY - 1

# The room starts one block beyond the arena.  The gate is a 3-wide by 4-high
# obsidian wall; the room and arena have no roof.
$gateX = 29; $gateMinY = 68; $gateMaxY = 71; $gateMinZ = -40; $gateMaxZ = -38
$roomMinX = 30; $roomMaxX = 38; $roomMinZ = -44; $roomMaxZ = -34
$roomTopY = 70
$roomClearTopY = $roomTopY + 1

# The world spawn is 0,64,-32.  The station is on the spawn-side plaza at
# z=-16, outside the arena's z=-19 boundary, so it cannot become part of the
# event arena while still being directly reachable from spawn.
$spawnStationX1 = 0; $spawnStationX2 = 3; $spawnStationY = 64; $spawnStationZ = -16
$spawnButtonZ = $spawnStationZ - 1

$datapackSource = Join-Path $scriptRoot 'assets\copimine-local-spawn'
$datapackTarget = Join-Path $ServerDir 'CopiMine\datapacks\copimine-local-spawn'
if (Test-Path -LiteralPath $datapackTarget) {
  Remove-Item -LiteralPath $datapackTarget -Recurse -Force
}
New-Item -ItemType Directory -Path (Split-Path $datapackTarget) -Force | Out-Null
Copy-Item -LiteralPath $datapackSource -Destination $datapackTarget -Recurse -Force

Invoke-SceneCommand 'reload confirm'
Invoke-SceneCommand 'datapack enable "file/copimine-local-spawn"'
Start-Sleep -Seconds 2
$datapacks = Invoke-SceneCommand 'datapack list enabled'
if ($datapacks -notmatch 'copimine-local-spawn') {
  throw "The local spawn datapack did not load. Actual response: $datapacks"
}

# Clear only the authorized local arena/room footprint, then build the stone
# floor, low stone arena boundary, portal room, active End portal, and the
# snapshot-owned obsidian gate.
foreach ($command in @(
  "execute in $world run fill $arenaMinX $arenaMinY $arenaMinZ $arenaMaxX $arenaMaxY $arenaMaxZ minecraft:air",
  "execute in $world run fill $arenaMinX $floorY $arenaMinZ $arenaMaxX $floorY $arenaMaxZ minecraft:stone",
  "execute in $world run fill $arenaMinX $coreY $arenaMinZ $arenaMaxX $coreY $arenaMinZ minecraft:stone",
  "execute in $world run fill $arenaMinX $coreY $arenaMaxZ $arenaMaxX $coreY $arenaMaxZ minecraft:stone",
  "execute in $world run fill $arenaMinX $coreY $arenaMinZ $arenaMinX $coreY $arenaMaxZ minecraft:stone",
  "execute in $world run fill $arenaMaxX $coreY $arenaMinZ $arenaMaxX $coreY $arenaMaxZ minecraft:stone",
  "execute in $world run setblock $coreX $coreY $coreZ minecraft:stone",
  "execute in $world run fill $roomMinX $floorY $roomMinZ $roomMaxX $roomClearTopY $roomMaxZ minecraft:air",
  "execute in $world run fill $roomMinX $floorY $roomMinZ $roomMaxX $floorY $roomMaxZ minecraft:stone",
  "execute in $world run fill $roomMinX $coreY $roomMinZ $roomMinX $roomTopY $roomMaxZ minecraft:stone",
  "execute in $world run fill $roomMaxX $coreY $roomMinZ $roomMaxX $roomTopY $roomMaxZ minecraft:stone",
  "execute in $world run fill $roomMinX $coreY $roomMinZ $roomMaxX $roomTopY $roomMinZ minecraft:stone",
  "execute in $world run fill $roomMinX $coreY $roomMaxZ $roomMaxX $roomTopY $roomMaxZ minecraft:stone",
  "execute in $world run fill $roomMinX $coreY -40 $roomMinX $roomTopY -38 minecraft:air",
  "execute in $world run fill $gateX $gateMinY $gateMinZ $gateX $gateMaxY $gateMaxZ minecraft:obsidian",
  "execute in $world run fill 32 68 -42 36 68 -42 minecraft:end_portal_frame",
  "execute in $world run fill 32 68 -36 36 68 -36 minecraft:end_portal_frame",
  "execute in $world run fill 32 68 -41 32 68 -37 minecraft:end_portal_frame",
  "execute in $world run fill 36 68 -41 36 68 -37 minecraft:end_portal_frame",
  "execute in $world run fill 33 68 -40 35 68 -38 minecraft:end_portal",
  "execute in $world run fill $spawnStationX1 $($spawnStationY - 1) $spawnStationZ $spawnStationX2 $($spawnStationY - 1) $spawnStationZ minecraft:stone",
  "execute in $world run fill $spawnStationX1 $spawnStationY $spawnStationZ $spawnStationX2 $spawnStationY $spawnStationZ minecraft:air",
  "execute in $world run setblock $spawnStationX1 $spawnStationY $spawnStationZ minecraft:command_block",
  "execute in $world run setblock $spawnStationX2 $spawnStationY $spawnStationZ minecraft:command_block",
  "execute in $world run setblock $spawnStationX1 $spawnStationY $spawnButtonZ minecraft:air",
  "execute in $world run setblock $spawnStationX2 $spawnStationY $spawnButtonZ minecraft:air",
  "execute in $world run setblock $spawnStationX1 $spawnStationY $spawnButtonZ minecraft:stone_button[face=wall,facing=south,powered=false]",
  "execute in $world run setblock $spawnStationX2 $spawnStationY $spawnButtonZ minecraft:stone_button[face=wall,facing=south,powered=false]",
  "execute in $world run setblock $spawnStationX1 $($spawnStationY + 1) $spawnStationZ minecraft:oak_sign[rotation=0]",
  "execute in $world run setblock $spawnStationX2 $($spawnStationY + 1) $spawnStationZ minecraft:oak_sign[rotation=0]",
  "execute in $world run data merge block $spawnStationX1 $spawnStationY $spawnStationZ {Command:`"function copimine:spawn/max_on`"}",
  "execute in $world run data merge block $spawnStationX2 $spawnStationY $spawnStationZ {Command:`"function copimine:spawn/max_clear`"}"
)) {
  Invoke-SceneCommand $command | Out-Null
}

<#
$enableSign = '{front_text:{messages:[''{'"text":"ЭФФЕКТЫ","color":"dark_purple"}'' , ''{'"text":"ВКЛЮЧИТЬ","color":"green"}'' , ''{'"text":"нажми кнопку","color":"gray"}'' , ''{'"text":"","color":"gray"}'']}}'
$clearSign = '{front_text:{messages:[''{'"text":"ЭФФЕКТЫ","color":"dark_purple"}'' , ''{'"text":"СНЯТЬ","color":"red"}'' , ''{'"text":"нажми кнопку","color":"gray"}'' , ''{'"text":"","color":"gray"}'']}}'
#>
<#
$enableSignMessages = @(
  '{"text":"ЭФФЕКТЫ","color":"dark_purple"}',
  '{"text":"ВКЛЮЧИТЬ","color":"green"}',
  '{"text":"нажми кнопку","color":"gray"}',
  '{"text":"","color":"gray"}'
)
$clearSignMessages = @(
  '{"text":"ЭФФЕКТЫ","color":"dark_purple"}',
  '{"text":"СНЯТЬ","color":"red"}',
  '{"text":"нажми кнопку","color":"gray"}',
  '{"text":"","color":"gray"}'
)
#>
function New-RussianText {
  param([int[]]$CodePoints)
  return -join ($CodePoints | ForEach-Object { [char]$_ })
}
$effectsText = New-RussianText @(0x042D,0x0424,0x0424,0x0415,0x041A,0x0422,0x042B)
$enableText = New-RussianText @(0x0412,0x041A,0x041B,0x042E,0x0427,0x0418,0x0422,0x042C)
$clearText = New-RussianText @(0x0421,0x041D,0x042F,0x0422,0x042C)
$pressText = (New-RussianText @(0x043D,0x0430,0x0436,0x043C,0x0438)) + ' ' + (New-RussianText @(0x043A,0x043D,0x043E,0x043F,0x043A,0x0443))
$enableSignMessages = @()
$enableSignMessages += '{"text":"' + $effectsText + '","color":"dark_purple"}'
$enableSignMessages += '{"text":"' + $enableText + '","color":"green"}'
$enableSignMessages += '{"text":"' + $pressText + '","color":"gray"}'
$enableSignMessages += '{"text":"","color":"gray"}'
$clearSignMessages = @()
$clearSignMessages += '{"text":"' + $effectsText + '","color":"dark_purple"}'
$clearSignMessages += '{"text":"' + $clearText + '","color":"red"}'
$clearSignMessages += '{"text":"' + $pressText + '","color":"gray"}'
$clearSignMessages += '{"text":"","color":"gray"}'
$enableSignEntries = @($enableSignMessages | ForEach-Object { "'$_'" }) -join ','
$clearSignEntries = @($clearSignMessages | ForEach-Object { "'$_'" }) -join ','
$enableSign = "{front_text:{messages:[" + $enableSignEntries + "]}}"
$clearSign = "{front_text:{messages:[" + $clearSignEntries + "]}}"
Invoke-SceneCommand "execute in $world run data merge block $spawnStationX1 $($spawnStationY + 1) $spawnStationZ $enableSign" | Out-Null
Invoke-SceneCommand "execute in $world run data merge block $spawnStationX2 $($spawnStationY + 1) $spawnStationZ $clearSign" | Out-Null

$status = Invoke-SceneCommand 'cmend status'
$gateInfo = Invoke-SceneCommand 'cmend gate info'
$portalInfo = Invoke-SceneCommand 'cmend portalroom info'
if ($status -notmatch 'core=.*CopiMine 8,68,-39' -or
    $status -notmatch 'arena=.*CopiMine \[-12,65,-59\].*\[28,71,-19\]' -or
    $status -notmatch 'coreOverlay=true' -or
    $status -notmatch 'runes=2/2' -or
    $gateInfo -notmatch 'gate\.pos1=.*CopiMine 29,68,-40.*pos2=.*CopiMine 29,71,-38' -or
    $gateInfo -notmatch 'gate\.status=.*RESTORED' -or
    $portalInfo -notmatch 'portalroom=.*CopiMine 31\.5,68\.0,-42\.5') {
  throw "Local scene metadata does not match the rebuilt scene. Status: $status Gate: $gateInfo Portal: $portalInfo"
}
Assert-SceneCondition 'closed gate is obsidian' "execute in $world run execute if block $gateX $gateMinY $gateMinZ minecraft:obsidian run time query gametime"
Assert-SceneCondition 'portal plane is active' "execute in $world run execute if block 33 68 -39 minecraft:end_portal run time query gametime"
Assert-SceneCondition 'portal room has no roof' "execute in $world run execute if block $roomMinX $roomClearTopY $roomMinZ minecraft:air run time query gametime"
Assert-SceneCondition 'enable station button is a wall button' "execute in $world run execute if block $spawnStationX1 $spawnStationY $spawnButtonZ minecraft:stone_button[face=wall,facing=south,powered=false] run time query gametime"
Assert-SceneCondition 'clear station button is a wall button' "execute in $world run execute if block $spawnStationX2 $spawnStationY $spawnButtonZ minecraft:stone_button[face=wall,facing=south,powered=false] run time query gametime"
Assert-SceneCommandText 'enable command block function' "execute in $world run data get block $spawnStationX1 $spawnStationY $spawnStationZ Command" 'function copimine:spawn/max_on'
Assert-SceneCommandText 'clear command block function' "execute in $world run data get block $spawnStationX2 $spawnStationY $spawnStationZ Command" 'function copimine:spawn/max_clear'
Write-Host "Local End Rift scene rebuilt at arena [$arenaMinX,$arenaMinY,$arenaMinZ]..[$arenaMaxX,$arenaMaxY,$arenaMaxZ]."
Write-Host "Obsidian gate: [$gateX,$gateMinY,$gateMinZ]..[$gateX,$gateMaxY,$gateMaxZ] (3 wide x 4 high; no roof)."
Write-Host "Portal room: [$roomMinX,$floorY,$roomMinZ]..[$roomMaxX,$roomTopY,$roomMaxZ], portal plane 33..35,68,-40..-38."
Write-Host "Spawn-side station: command blocks $spawnStationX1,$spawnStationY,$spawnStationZ and $spawnStationX2,$spawnStationY,$spawnStationZ."
