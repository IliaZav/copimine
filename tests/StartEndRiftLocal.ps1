[CmdletBinding()]
param(
  [string]$ServerDir = '',
  [string]$LogPath = '',
  [string]$ErrPath = '',
  [int]$ReadyTimeoutSeconds = 45
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
  throw 'Refused to start a server outside this worktree local-runtime directory.'
}

$propertiesPath = Join-Path $ServerDir 'server.properties'
$eventConfigPath = Join-Path $ServerDir 'plugins\CopiMineEndEvent\config.yml'
$envPath = Join-Path $localRuntimeRoot 'end-rift.env'
if ((-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) -or (-not (Test-Path -LiteralPath $eventConfigPath -PathType Leaf))) {
  throw 'Local End Rift server.properties or plugin config is missing.'
}
if ((Get-Content -LiteralPath $eventConfigPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
  throw 'Refused to start a non-local End Rift configuration.'
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  if ($line -match '^([^#=]+)=(.*)$') {
    $properties[$matches[1].Trim()] = $matches[2].Trim()
  }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576') {
  throw 'Refused: local End Rift server ports must be 25566 and 25576.'
}
foreach ($port in @(25566, 25576)) {
  $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
  if ($listeners.Count -gt 0) {
    throw "Refused to start because local port $port is already listening."
  }
}

if (Test-Path -LiteralPath $envPath -PathType Leaf) {
  foreach ($line in Get-Content -LiteralPath $envPath) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      Set-Item -Path ("Env:" + $matches[1]) -Value $matches[2]
    }
  }
}

$localLogRoot = $localRuntimeRoot
if ([string]::IsNullOrWhiteSpace($LogPath)) {
  $LogPath = Join-Path $localLogRoot 'end-rift-paper-start.out.log'
}
if ([string]::IsNullOrWhiteSpace($ErrPath)) {
  $ErrPath = Join-Path $localLogRoot 'end-rift-paper-start.err.log'
}
$java = (Get-Command java -ErrorAction Stop).Source
$paper = Start-Process -FilePath $java `
  -ArgumentList @('-Xms2G', '-Xmx6G', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled', '-Dfile.encoding=UTF-8', '-jar', 'purpur.jar', 'nogui') `
  -WorkingDirectory $ServerDir `
  -RedirectStandardOutput $LogPath `
  -RedirectStandardError $ErrPath `
  -WindowStyle Hidden `
  -PassThru

$ready = $false
for ($second = 0; $second -lt $ReadyTimeoutSeconds; $second++) {
  if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
    $ready = (Get-Content -LiteralPath $LogPath -Raw) -match 'Done \([^\r\n]*\)!'
    if ($ready) { break }
  }
  Start-Sleep -Seconds 1
}
if (-not $ready) {
  if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
    Get-Content -LiteralPath $LogPath -Tail 120
  }
  throw "Local Paper PID $($paper.Id) did not become ready within $ReadyTimeoutSeconds seconds."
}

Write-Output "Started isolated local Paper PID=$($paper.Id) server=$ServerDir"
Write-Output 'Local End Rift ports: server=25566 rcon=25576'
