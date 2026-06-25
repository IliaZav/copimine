$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
$client = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientBridgeProtocol.java')
$server = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'opt\copimine\copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')
if ($client -notmatch 'copimine:client_bridge' -or $server -notmatch 'copimine:client_bridge') { throw 'Client and server bridge channels must match.' }
if ($client -notmatch 'visual_start' -or $server -notmatch 'visual_start') { throw 'visual_start payload missing.' }
Write-Host 'CopiMineClient networking channel validation passed.'
