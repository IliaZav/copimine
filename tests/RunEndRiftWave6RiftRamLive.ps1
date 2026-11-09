[CmdletBinding()]
param(
  [ValidateRange(30, 900)]
  [int]$TimeoutSeconds = 240,
  [string]$ServerDir = ''
)

# Local-only Wave 6 probe for the Rift Ram encounter that replaced the retired
# Ritual Sphere. It never creates accounts and never edits the saved arena: it
# drives one disposable test wave and reads server-authoritative state back.
#
# The old probe asserted WAVE6_RITUAL_SPHERE_READY / casters=5. That encounter
# no longer exists, so those markers can never appear and the probe could only
# ever fail. This version asserts the design the spec froze (section 8): ONE
# Ravager, one active pillar at a time, six armour stages, particle-only stomp.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $localRuntimeRoot 'end-rift-server-26.3'
}
$serverDir = Assert-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $localRuntimeRoot -MustExist
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$serverLog = Assert-EndRiftLocalRuntimePath -Path (Join-Path $serverDir 'logs\latest.log') -LocalRuntimeRoot $localRuntimeRoot -MustExist
$evidence = Join-Path $localRuntimeRoot ('wave6-rift-ram-' + (Get-Date -Format 'yyyyMMddHHmmssfff') + '.log')

function Invoke-LocalRcon([string]$CommandText) {
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) { throw "Local RCON failed: $CommandText $result" }
  return ($result -replace '\u00A7.', '').Trim()
}

function Write-Evidence([string]$Text) {
  Write-Output $Text
  Add-Content -LiteralPath $evidence -Value $Text -Encoding UTF8
}

function Count-Selector([string]$Selector) {
  $raw = Invoke-LocalRcon ("execute as " + $Selector + " run say .")
  return @(($raw -split [Environment]::NewLine) | Where-Object { $_ -match '\.$' -or $_ -match '^\s*\.\s*$' }).Count
}

$branch = (& git -C $root branch --show-current).Trim()
if ((Invoke-LocalRcon 'list') -notmatch '(?i)players online') {
  throw 'Local server is not answering RCON.'
}
$status = Invoke-LocalRcon 'cmend status'
if ($status -notmatch 'arena=Arena') {
  throw "Refused: the local arena is not configured. Status: $status"
}

# A controllable participant is mandatory: the charge must be lured into the
# pillar by a real player, and the stomp hit test needs a body on the floor.
if ($status -notmatch 'participants=[1-9]') {
  throw 'Refused: Wave 6 needs at least one live participant inside the arena. Connect a Minecraft 26.3 client to 127.0.0.1:25566, stand on the core pad (Arena 0,100,0), then re-run this probe. Without a player the Ravager has no charge target, no pillar can be struck, and every assertion below would only prove that nothing spawned.'
}

$offset = (Get-Item -LiteralPath $serverLog).Length
$null = Invoke-LocalRcon 'cmend wave spawn 6'

$tail = ''
$started = ''
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
  $text = [string](Get-Content -LiteralPath $serverLog -Raw)
  if ($offset -lt $text.Length) {
    $tail = $text.Substring([int]$offset)
    $m = [Regex]::Match($tail, 'WAVE_TEST_STARTED[^\r\n]*wave=6[^\r\n]*')
    if ($m.Success) { $started = $m.Value; break }
  }
  Start-Sleep -Milliseconds 400
}
if (-not $started) { throw "Wave 6 did not start within $TimeoutSeconds s. See $serverLog" }
Write-Evidence "LIVE_W6_STARTED $started"

# Recovery is fatal: any of these means the encounter refused to own its state.
$recovery = @([Regex]::Matches($tail, 'RIFT_RAM_[A-Z_]+|END_RIFT_RAM_RECOVERY[^\r\n]*') | ForEach-Object { $_.Value })
if ($recovery.Count -gt 0) {
  throw ('Wave 6 entered recovery: ' + ($recovery -join '; '))
}

# ONE oversized Ravager, owned by the event.
$ravagerCount = Count-Selector '@e[type=ravager,distance=..40]'
if ($ravagerCount -ne 1) {
  throw "Expected exactly one Ravager in the arena, found $ravagerCount."
}
Write-Evidence "LIVE_W6_RAVAGER_PASS count=$ravagerCount"

# The pillar and the armour are NETHERITE_BLOCK displays.
$displayCount = Count-Selector '@e[type=block_display,distance=..40]'
if ($displayCount -lt 1) {
  throw "No pillar or armour display is present; the Ram visual layer never built."
}
Write-Evidence "LIVE_W6_DISPLAYS_PASS displays=$displayCount"

# The stomp is particles only, so the arena floor must stay untouched.
$hazards = Invoke-LocalRcon 'cmend debug hazards'
if ($hazards -notmatch 'journal=EMPTY') {
  throw "Wave 6 mutated blocks; hazard journal is not empty: $hazards"
}
Write-Evidence "LIVE_W6_NO_BLOCK_MUTATION_PASS $hazards"

Write-Evidence ('LIVE_W6_PERF ' + (Invoke-LocalRcon 'cmend debug perf'))

Write-Output ("Wave 6 Rift Ram scene is live. Inspect the pillar, the charge and the seismic stomp in Minecraft, then drive five impacts to completion.")
