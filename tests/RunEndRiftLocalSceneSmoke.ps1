[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [int]$RconPort = 25576
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$worktreeRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $worktreeRoot 'local-runtime')).Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $localRuntimeRoot 'end-rift-server'
}
$ServerDir = (Resolve-Path $ServerDir).Path
$localPrefix = $localRuntimeRoot.TrimEnd('\') + '\'
if (-not $ServerDir.StartsWith($localPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Local scene smoke refuses a server outside this worktree local-runtime directory.'
}
$propertiesPath = Join-Path $ServerDir 'server.properties'
$configPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\config.yml'
if (-not (Test-Path -LiteralPath $propertiesPath) -or -not (Test-Path -LiteralPath $configPath)) {
  throw 'Local scene smoke requires the isolated End Rift server files.'
}
if ((Get-Item -LiteralPath $propertiesPath).Length -gt 1048576) {
  throw 'Local scene smoke refused an unexpectedly large server.properties file.'
}
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Local scene smoke refused a non-local End Rift configuration.'
}
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  if ($line -match '^([^#=]+)=(.*)$') {
    $properties[$matches[1].Trim()] = $matches[2].Trim()
  }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576' -or $RconPort -ne 25576) {
  throw 'Local scene smoke requires the isolated 25566/25576 ports.'
}

$rconHelper = Join-Path $scriptRoot 'InvokeEndRiftLocalRcon.ps1'
function Invoke-SceneRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoProfile -ExecutionPolicy Bypass -File $rconHelper `
    -ServerDir $ServerDir -RconPort $RconPort -CommandText $CommandText
  if ($LASTEXITCODE -ne 0) {
    throw "Local scene RCON failed for '$CommandText'. Output: $result"
  }
  return ($result -join [Environment]::NewLine)
}

function Assert-Text {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$Text,
    [Parameter(Mandatory = $true)][string]$Expected
  )
  if ($Text -notmatch [regex]::Escape($Expected)) {
    throw "$Label expected '$Expected'. Actual response: $Text"
  }
  Write-Host "PASS $Label"
}

function Assert-Condition {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$CommandText
  )
  $result = Invoke-SceneRcon $CommandText
  if ($result -notmatch 'The time is') {
    throw "$Label condition was false. Actual response: $result"
  }
  Write-Host "PASS $Label"
}

$world = 'minecraft:overworld'
$gateMinX = 29; $gateMinY = 68; $gateMinZ = -40
$gateMaxX = 29; $gateMaxY = 71; $gateMaxZ = -38
$portalX = 33; $portalY = 68; $portalZ = -39
$status = Invoke-SceneRcon 'cmend status'
Assert-Text 'scene state' $status 'COLLECTING'
Assert-Text 'scene core' $status '8,68,-39'
Assert-Text 'scene visuals' $status 'coreOverlay=true'
Assert-Text 'scene runes' $status 'runes=2/2'
$gateInfo = Invoke-SceneRcon 'cmend gate info'
Assert-Text 'gate coordinates' $gateInfo '29,68,-40'
Assert-Text 'gate closed state' $gateInfo 'RESTORED'
$portalInfo = Invoke-SceneRcon 'cmend portalroom info'
Assert-Text 'portal room metadata' $portalInfo '31.5,68.0,-42.5'

Assert-Condition 'closed gate is obsidian' "execute in $world run execute if block $gateMinX $gateMinY $gateMinZ minecraft:obsidian run minecraft:time query gametime"
Assert-Condition 'portal remains active before opening' "execute in $world run execute if block $portalX $portalY $portalZ minecraft:end_portal run minecraft:time query gametime"
Assert-Condition 'portal room is roofless' "execute in $world run execute if block 30 71 -44 minecraft:air run minecraft:time query gametime"
Assert-Condition 'enable station button exists' "execute in $world run execute if block 0 64 -17 minecraft:stone_button[face=wall,facing=south,powered=false] run minecraft:time query gametime"
Assert-Condition 'clear station button exists' "execute in $world run execute if block 3 64 -17 minecraft:stone_button[face=wall,facing=south,powered=false] run minecraft:time query gametime"
Assert-Text 'enable station function' (Invoke-SceneRcon 'execute in minecraft:overworld run data get block 0 64 -16 Command') 'function copimine:spawn/max_on'
Assert-Text 'clear station function' (Invoke-SceneRcon 'execute in minecraft:overworld run data get block 3 64 -16 Command') 'function copimine:spawn/max_clear'
Invoke-SceneRcon 'execute in minecraft:overworld run setblock 0 64 -17 minecraft:stone_button[face=wall,facing=south,powered=true]' | Out-Null
Assert-Text 'enable button powers command block' (Invoke-SceneRcon 'execute in minecraft:overworld run data get block 0 64 -16 powered') '1b'
Invoke-SceneRcon 'execute in minecraft:overworld run setblock 0 64 -17 minecraft:stone_button[face=wall,facing=south,powered=false]' | Out-Null
Assert-Text 'enable button resets command block power' (Invoke-SceneRcon 'execute in minecraft:overworld run data get block 0 64 -16 powered') '0b'
Invoke-SceneRcon 'execute in minecraft:overworld run setblock 3 64 -17 minecraft:stone_button[face=wall,facing=south,powered=true]' | Out-Null
Assert-Text 'clear button powers command block' (Invoke-SceneRcon 'execute in minecraft:overworld run data get block 3 64 -16 powered') '1b'
Invoke-SceneRcon 'execute in minecraft:overworld run setblock 3 64 -17 minecraft:stone_button[face=wall,facing=south,powered=false]' | Out-Null
Assert-Text 'clear button resets command block power' (Invoke-SceneRcon 'execute in minecraft:overworld run data get block 3 64 -16 powered') '0b'

$restoreNeeded = $false
try {
  $open = Invoke-SceneRcon 'cmend gate open 1'
  Assert-Text 'gate open command accepted' $open 'Gate'
  $restoreNeeded = $true
  $opened = $false
  for ($attempt = 0; $attempt -lt 20; $attempt++) {
    $gateInfo = Invoke-SceneRcon 'cmend gate info'
    if ($gateInfo -match 'OPENED' -and $gateInfo -match '12/12') {
      $opened = $true
      break
    }
    Start-Sleep -Milliseconds 250
  }
  if (-not $opened) {
    throw "Gate did not reach OPENED/12/12. Actual response: $gateInfo"
  }
  Write-Host 'PASS gate reaches OPENED 12/12'
  Assert-Condition 'gate passage becomes air' "execute in $world run execute if block 29 69 -39 minecraft:air run minecraft:time query gametime"
  $remaining = Invoke-SceneRcon 'execute in minecraft:overworld run fill 29 68 -40 29 71 -38 minecraft:air replace minecraft:obsidian'
  Assert-Text 'all twelve gate blocks disappeared' $remaining 'No blocks were filled'
  Assert-Condition 'portal remains active after opening' "execute in $world run execute if block $portalX $portalY $portalZ minecraft:end_portal run minecraft:time query gametime"
}
finally {
  if ($restoreNeeded) {
    $restore = Invoke-SceneRcon 'cmend gate restore confirm'
    Assert-Text 'gate restore command accepted' $restore 'Gate'
  }
}

$finalGateInfo = Invoke-SceneRcon 'cmend gate info'
Assert-Text 'gate final state restored' $finalGateInfo 'RESTORED'
Assert-Condition 'gate final blocks restored' "execute in $world run execute if block 29 69 -39 minecraft:obsidian run minecraft:time query gametime"
Assert-Condition 'portal final state remains active' "execute in $world run execute if block $portalX $portalY $portalZ minecraft:end_portal run minecraft:time query gametime"
Write-Host 'End Rift local scene smoke passed.'
