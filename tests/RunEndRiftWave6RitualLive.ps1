[CmdletBinding()]
param(
  [ValidateRange(30, 600)]
  [int]$TimeoutSeconds = 180
)

# Local-only Wave 6 smoke probe. Two players must connect and operate the
# encounter manually; the probe never creates accounts or stores credentials.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$pluginConfig = Join-Path $root 'copimine-end-event/config.yml'
$serverPlugin = Join-Path $serverDir 'plugins/CopiMineEndEvent.jar'
$sourcePlugin = Join-Path $root 'copimine-end-event/CopiMineEndEvent.jar'
$rconScript = Join-Path $root 'tests/InvokeEndRiftLocalRcon.ps1'
$serverLog = Join-Path $serverDir 'logs/latest.log'
$evidence = Join-Path $runtimeRoot ('wave6-stage1-' + (Get-Date -Format 'yyyyMMddHHmmssfff') + '.log')

function Test-LocalPort([int]$Port) {
  $client = [Net.Sockets.TcpClient]::new()
  try { $client.Connect('127.0.0.1', $Port); return $true }
  catch { return $false }
  finally { $client.Dispose() }
}

function Invoke-LocalRcon([string]$CommandText) {
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "Local RCON failed: $CommandText $result" }
  return $result.Trim()
}

function Write-Evidence([string]$Text) {
  Write-Output $Text
  Add-Content -LiteralPath $evidence -Value $Text -Encoding UTF8
}

function Wait-Log([string]$Pattern, [int64]$Offset, [int]$Seconds = $TimeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $text = [string](Get-Content -LiteralPath $serverLog -Raw)
    if ($Offset -lt $text.Length) {
      $tail = $text.Substring([int]$Offset)
      if ($tail -match $Pattern) { return $Matches[0] }
    }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'. See $serverLog"
}

function Wait-Players([int]$Minimum, [int]$Seconds) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $list = Invoke-LocalRcon 'list'
    $match = [Regex]::Match($list, 'players online:\s*(.*)$', 'IgnoreCase')
    if ($match.Success) {
      $names = @($match.Groups[1].Value -split ',\s*' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
      if ($names.Count -ge $Minimum) { return $names }
    }
    Start-Sleep -Seconds 2
  }
  throw "Connect at least $Minimum local players to 127.0.0.1:25566 before running this probe."
}

$branch = (& git -C $root branch --show-current).Trim()
if ($branch -ne 'codex/end-rift-event') { throw "Refused branch '$branch'." }
if ((Get-Content -LiteralPath $pluginConfig -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused: End Rift config is not environment: local.'
}
if (-not (Test-Path -LiteralPath $sourcePlugin -PathType Leaf)) {
  throw "Build the current End Rift plugin first: $sourcePlugin"
}
if ((Test-LocalPort 25566) -xor (Test-LocalPort 25576)) {
  throw 'Refused: only one isolated server port is listening; resolve the local server state first.'
}
if (-not (Test-LocalPort 25576)) {
  & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'tests/StartEndRiftLocal.ps1')
  if ($LASTEXITCODE -ne 0) { throw 'Isolated local Paper startup failed.' }
}

$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourcePlugin).Hash
$activeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverPlugin).Hash
if ($sourceHash -ne $activeHash) {
  throw "Refused to interrupt an active server. Plugin hash differs: source=$sourceHash active=$activeHash."
}
$status = (Invoke-LocalRcon 'cmend status') -replace '\u00A7.', ''
if ($status -notmatch 'state=READY_FOR_PLAYERS' -or $status -match 'boss=\S+ hp=') {
  throw "Refused to alter an active End Rift event. Current status: $status"
}
$players = Wait-Players -Minimum 2 -Seconds $TimeoutSeconds
Write-Evidence "LOCAL_W6_SERVER_READY address=127.0.0.1:25566 rcon=127.0.0.1:25576 players=$($players -join ',') plugin_sha256=$sourceHash"

$offset = (Get-Item -LiteralPath $serverLog).Length
$null = Invoke-LocalRcon 'cmend test wave 6'
$ready = Wait-Log -Pattern 'WAVE6_RITUAL_SPHERE_READY[^\r\n]*state=WAITING_FOR_PRISONER[^\r\n]*casters=5[^\r\n]*authority=server' -Offset $offset -Seconds $TimeoutSeconds
$aiRaw = (Invoke-LocalRcon 'cmend debug ai --json') -replace '\u00A7.', ''
try { $ai = $aiRaw | ConvertFrom-Json -ErrorAction Stop }
catch { throw "Wave 6 AI diagnostic was not JSON: $aiRaw" }
$casters = if ($null -eq $ai.casters) { @() } else { @($ai.casters) }
if ($casters.Count -ne 5 -or [int]$ai.ritualCasters -ne 5 -or -not [bool]$ai.ritualGuardOwnershipValid) {
  throw "Wave 6 roster is incomplete: $($aiRaw | ConvertTo-Json -Depth 10 -Compress)"
}
Write-Evidence "LIVE_W6_ROSTER_PASS casters=$($casters.Count) guards=$([int]$ai.ritualGuards) guarded=$(@($casters | Where-Object state -eq 'GUARDED_CASTING').Count)"
$objectives = (Invoke-LocalRcon 'cmend debug objectives') -replace '\u00A7.', ''
if ($objectives -notmatch 'visuals=\s*[1-9]\d*') { throw "Wave 6 sphere visual was not registered: $objectives" }
Write-Evidence "LIVE_W6_SCENE_PASS ready_marker=$ready objectives=$objectives"
Write-Output 'Wave 6 scene is ready. Connect or remain connected at 127.0.0.1:25566 to inspect it in Minecraft.'
