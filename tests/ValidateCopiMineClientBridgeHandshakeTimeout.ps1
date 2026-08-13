$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
if ($config -match 'handshake_timeout_seconds|require_client_mod|kick_if_missing_client|required_mod_ids') { throw 'Retired client-mod handshake settings remain in config.' }
if ($bridge -match 'runTaskLater|enforceClientModpack|missing-required-mods:|kickPlayer\(') { throw 'Client bridge must not delay, reject, or kick players for client mods.' }
Write-Host 'Client bridge no-mod-gate timeout validation passed.'
