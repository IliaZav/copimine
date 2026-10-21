[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [ValidateLength(1, 4096)]
  [string]$CommandText,
  [string]$ServerDir = '',
  [ValidateRange(1, 65535)]
  [int]$ServerPort = 25566,
  [ValidateRange(1, 65535)]
  [int]$RconPort = 25576
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot '..')).Path
. (Join-Path $scriptRoot 'EndRiftLocalAuthMode.ps1')
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
  $ServerDir = Join-Path $repoRoot 'local-runtime\end-rift-server'
}
$localRuntimeRoot = (Resolve-Path (Join-Path $repoRoot 'local-runtime')).Path
$ServerDir = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $ServerDir).ProviderPath)
$ServerDir = Test-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $localRuntimeRoot -MustExist
$propertiesPath = Join-Path $ServerDir 'server.properties'
$propertiesPath = Test-EndRiftLocalRuntimePath -Path $propertiesPath -LocalRuntimeRoot $localRuntimeRoot -MustExist
if (-not (Test-Path -LiteralPath $propertiesPath)) {
  throw 'Local RCON server.properties is missing.'
}
$serverPropertiesLines = [IO.File]::ReadAllLines($propertiesPath)
Test-EndRiftLocalServerBind -ServerPropertiesLines $serverPropertiesLines | Out-Null
$rconPortRows = @($serverPropertiesLines | Where-Object { $_ -match '^rcon\.port=' })
if ($rconPortRows.Count -ne 1 -or $rconPortRows[0] -cne ("rcon.port=" + $RconPort)) {
  throw 'Local RCON port does not match the isolated server configuration.'
}
$portReceiptPath = Join-Path $ServerDir ('.end-rift-process-receipt-' + $ServerPort + '.json')
$receiptPath = if (Test-Path -LiteralPath $portReceiptPath -PathType Leaf) {
  $portReceiptPath
} else {
  Join-Path $ServerDir '.end-rift-process-receipt.json'
}
$serverReceipt = Get-EndRiftLocalServerReceipt -ServerDir $ServerDir -LocalRuntimeRoot $localRuntimeRoot -ReceiptPath $receiptPath
$expectedPaperJarPath = Join-Path $ServerDir 'paper-26.3-205.jar'
if (-not (Test-Path -LiteralPath $expectedPaperJarPath -PathType Leaf)) {
  $expectedPaperJarPath = Join-Path $ServerDir 'purpur.jar'
}
$expectedPaperJarPath = Assert-EndRiftLocalRuntimePath -Path $expectedPaperJarPath -LocalRuntimeRoot $localRuntimeRoot -MustExist
$canonicalReceiptPaperJarPath = Resolve-EndRiftLocalDriveSubstitutions -Path ([string]$serverReceipt.PaperJarPath)
if (-not [string]::Equals($canonicalReceiptPaperJarPath, $expectedPaperJarPath, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Local RCON receipt does not identify the Paper JAR in the selected isolated server directory.'
}
$identityMatches = Test-EndRiftLocalServerProcessIdentity `
  -ExpectedProcessId $serverReceipt.ProcessId `
  -ExpectedProcessStartTimeUtcTicks $serverReceipt.ProcessStartTimeUtcTicks `
  -ServerDir $ServerDir `
  -PaperJarPath $serverReceipt.PaperJarPath `
  -ExpectedWorkingDirectory $serverReceipt.WorkingDirectory `
  -LocalRuntimeRoot $localRuntimeRoot
if (-not $identityMatches) {
  throw 'Local RCON refused: the Paper process does not match the isolated server receipt.'
}
Assert-EndRiftLocalServerBind -ServerDir $ServerDir `
  -ServerPort $ServerPort `
  -RconPort $RconPort `
  -ExpectedProcessId $serverReceipt.ProcessId | Out-Null
$rconPasswordLines = @()
foreach ($line in [IO.File]::ReadLines($propertiesPath)) {
  if ($line.StartsWith('rcon.password=', [StringComparison]::Ordinal)) { $rconPasswordLines += $line }
}
$rconPasswordCount = $rconPasswordLines.Count
if ($rconPasswordCount -ne 1) {
  throw 'Local RCON server.properties must contain one password setting.'
}
$rconPassword = ''
$rconPassword = [string]$rconPasswordLines[0].Substring('rcon.password='.Length).Trim()
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
  $client.ReceiveTimeout = 5000
  $client.SendTimeout = 5000
  $client.Connect('127.0.0.1', $RconPort)
  $script:stream = $client.GetStream()
  Write-Packet 62001 3 $rconPassword
  $auth = Read-Packet
  if ($auth.Id -eq -1 -or $auth.Id -ne 62001) { throw 'Local RCON authentication failed or returned an invalid response.' }
  Write-Packet 62002 2 $CommandText
  $response = Read-Packet
  if ($response.Id -ne 62002) { throw 'Local RCON returned a response for another request.' }
  Write-Output $response.Body
} finally {
  if ($script:stream) { $script:stream.Dispose() }
  $client.Dispose()
  $script:stream = $null
}
