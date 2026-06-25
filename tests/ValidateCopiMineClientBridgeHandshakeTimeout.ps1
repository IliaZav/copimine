$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
if ($config -notmatch 'handshake_timeout_seconds') { throw 'Config missing handshake_timeout_seconds.' }
if ($bridge -notmatch 'runTaskLater' -or $bridge -notmatch 'handshakeTimeoutSeconds') { throw 'Bridge missing handshake timeout enforcement path.' }
Write-Host 'Client bridge handshake timeout validation passed.'