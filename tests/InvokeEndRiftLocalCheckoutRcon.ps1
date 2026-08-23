[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$CommandText,
    [Parameter(Mandatory = $true)]
    [string]$ServerDir,
    [int]$RconPort = 25575
)

$ErrorActionPreference = 'Stop'
$serverPath = (Resolve-Path -LiteralPath $ServerDir).Path
$checkoutRoot = (Resolve-Path (Join-Path $serverPath '..\..')).Path
$checkoutName = Split-Path $checkoutRoot -Leaf
if ($checkoutName -notmatch '^copimine-local-[^\\/]+$') {
    throw 'External local RCON refuses a checkout that is not named copimine-local-*.'
}
$configPath = Join-Path $serverPath 'plugins\CopiMineEndEvent\config.yml'
$propertiesPath = Join-Path $serverPath 'server.properties'
if (-not (Test-Path -LiteralPath $configPath) -or -not (Test-Path -LiteralPath $propertiesPath)) {
    throw 'External local RCON requires server.properties and CopiMineEndEvent config.'
}
if ((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^environment:\s*local\s*$') {
    throw 'External local RCON refuses a non-local End Rift configuration.'
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ($line -match '^([^#=]+)=(.*)$') {
        $properties[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$rconPassword = $properties['rcon.password']
if ([string]::IsNullOrWhiteSpace($rconPassword)) {
    throw 'External local RCON password is missing.'
}

function Read-Exact {
    param([int]$Length)
    $buffer = [byte[]]::new($Length)
    $offset = 0
    while ($offset -lt $Length) {
        $read = $script:stream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) { throw 'External local RCON connection closed.' }
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
    if ($size -lt 10 -or $size -gt 8192) { throw "Invalid external local RCON packet size: $size" }
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
    Write-Packet 63001 3 $rconPassword
    $auth = Read-Packet
    if ($auth.Id -eq -1) { throw 'External local RCON authentication failed.' }
    Write-Packet 63002 2 $CommandText
    (Read-Packet).Body
} finally {
    if ($script:stream) { $script:stream.Dispose() }
    $client.Dispose()
    $script:stream = $null
}
