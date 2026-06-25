$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$protocol = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\CopiMineClient.java')
if ($protocol -notmatch 'MAX_HELLO_ATTEMPTS') { throw 'Handshake retry attempts constant missing.' }
if ($protocol -notmatch 'HELLO_RETRY_INTERVAL_MS') { throw 'Handshake retry interval missing.' }
if ($protocol -notmatch 'tickHelloRetry') { throw 'Handshake retry tick method missing.' }
if ($protocol -notmatch 'handshakeStatusLine') { throw 'Handshake status output missing.' }
if ($client -notmatch 'ClientBridgeProtocol\.tickNetwork') { throw 'Client tick must drive bridge networking, including handshake retry.' }
Write-Host 'CopiMineClient handshake retry validation passed.'
