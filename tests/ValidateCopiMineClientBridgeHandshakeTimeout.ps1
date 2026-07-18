$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
if ($config -notmatch 'handshake_timeout_seconds') { throw 'Config missing handshake_timeout_seconds.' }
if ($bridge -match 'kickPlayer\(' -or $bridge -notmatch 'cannot prove that a client mod is installed') { throw 'A plugin-channel handshake must not be used as proof for automatic client-mod kicking.' }
Write-Host 'Client bridge handshake safety validation passed.'
