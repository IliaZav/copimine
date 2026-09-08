[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$AdminNickname = 'SudoKillDash9'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (Resolve-Path (Join-Path $scriptRoot '..')).Path
$serverDir = (Resolve-Path (Join-Path $root 'local-runtime\end-rift-server')).Path
$rconScript = Join-Path $scriptRoot 'InvokeEndRiftLocalRcon.ps1'
$propertiesPath = Join-Path $serverDir 'server.properties'

foreach ($path in @($serverDir, $rconScript, $propertiesPath)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf) -and $path -ne $serverDir) {
    throw "Required local real-health probe file is missing: $path"
  }
}

$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused to run outside codex/end-rift-event: $branch"
}
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath -Encoding UTF8) {
  if ($line -match '^([^=]+)=(.*)$') { $properties[$matches[1]] = $matches[2] }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576') {
  throw 'Real-health probe requires the isolated local server ports 25566/25576.'
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "RCON failed for '$CommandText': $result" }
  return $result
}

function Plain([string]$Text) {
  return ($Text -replace '\u00A7.', '')
}

function BossUuid([string]$Status) {
  $plain = Plain $Status
  if ($plain -match 'boss=([0-9a-f-]{36})\s+hp=') { return $matches[1] }
  return $null
}

function Assert-Text {
  param([string]$Label, [string]$Text, [string]$Expected)
  if ($Text -notmatch [Regex]::Escape($Expected)) {
    throw "$Label missing '$Expected': $Text"
  }
}

$baselineCore = '8 68 -39 2'
$bossUuid = $null
try {
  Invoke-LocalRcon 'cmend boss kill cleanup' | Out-Null
  Invoke-LocalRcon 'cmend core remove confirm' | Out-Null
  Invoke-LocalRcon "cmend core setat $baselineCore" | Out-Null
  Invoke-LocalRcon 'cmend boss spawn official confirm' | Out-Null
  Start-Sleep -Seconds 2

  $status = Plain (Invoke-LocalRcon 'cmend status')
  $bossUuid = BossUuid $status
  if ([string]::IsNullOrWhiteSpace($bossUuid)) { throw "Official boss UUID was not reported: $status" }
  Assert-Text 'official entity health status' $status 'hp=5000/5000 physical=5000/5000'

  $health = Plain (Invoke-LocalRcon "data get entity $bossUuid Health")
  Assert-Text 'entity Health NBT' $health '5000.0f'
  $max = Plain (Invoke-LocalRcon "attribute $bossUuid minecraft:generic.max_health get")
  Assert-Text 'entity max-health attribute' $max '5000.0'
  $pdc = Plain (Invoke-LocalRcon "data get entity $bossUuid BukkitValues.`"copimineendevent:end_event_boss_v2_max_health`"")
  Assert-Text 'V2 max-health marker' $pdc '5000.0d'
  $legacyPdc = Plain (Invoke-LocalRcon "data get entity $bossUuid BukkitValues.`"copimineendevent:end_event_boss_virtual_health`"")
  if ($legacyPdc -notmatch 'No value|No element|Found no') {
    throw "Official V2 boss still carries a legacy virtual-health marker: $legacyPdc"
  }

  Write-Output "LIVE_BOSS_REAL_HEALTH_PASS boss=$bossUuid status=hp-5000/5000 physical-5000/5000 attribute-unclamped=true legacy-virtual-marker=false"
}
finally {
  try { Invoke-LocalRcon 'cmend boss kill cleanup' | Out-Null } catch { }
  try { Invoke-LocalRcon 'cmend core remove confirm' | Out-Null } catch { }
  try { Invoke-LocalRcon "cmend core setat $baselineCore" | Out-Null } catch { }
}
