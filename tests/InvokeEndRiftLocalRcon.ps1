[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$CommandText,
  [string]$ServerDir = '',
  [int]$RconPort = 25576
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $repoRoot 'local-runtime\end-rift-server'
}
$ServerDir = (Resolve-Path $ServerDir).Path
$localRuntimeRoot = (Resolve-Path (Join-Path $repoRoot 'local-runtime')).Path
if (-not $ServerDir.StartsWith($localRuntimeRoot, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Local RCON refuses a server path outside this worktree local-runtime directory.'
}
$propertiesPath = Join-Path $ServerDir 'server.properties'
if (-not (Test-Path -LiteralPath $propertiesPath)) {
  throw 'Local RCON server.properties is missing.'
}
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  if ($line -match '^([^#=]+)=(.*)$') {
    $properties[$matches[1].Trim()] = $matches[2].Trim()
  }
}
$rconPassword = $properties['rcon.password']
if ([string]::IsNullOrWhiteSpace($rconPassword)) {
  throw 'Local RCON password is missing.'
}

function Read-Exact {
  param([int]$Length)
  $buffer = [byte[]]::new($Length)
  $offset = 0
  while ($offset -lt $Length) {
    $read = $script:stream.Read($buffer, $offset, $Length - $offset)
    if ($read -le 0) { throw 'Local RCON connection closed.' }
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
  if ($size -lt 10 -or $size -gt 8192) { throw "Invalid local RCON packet size: $size" }
  $payload = Read-Exact $size
  [pscustomobject]@{
    Id = [BitConverter]::ToInt32($payload, 0)
    Body = [Text.Encoding]::UTF8.GetString($payload, 8, $size - 10)
  }
}

$client = [Net.Sockets.TcpClient]::new()
try {
  $client.Connect('127.0.0.1', $RconPort)
  $script:stream = $client.GetStream()
  Write-Packet 62001 3 $rconPassword
  $auth = Read-Packet
  if ($auth.Id -eq -1) { throw 'Local RCON authentication failed.' }
  Write-Packet 62002 2 $CommandText
  $response = Read-Packet
  Write-Output $response.Body
} finally {
  if ($script:stream) { $script:stream.Dispose() }
  $client.Dispose()
  $script:stream = $null
}
