$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
if ($config -match 'require_client_mod|kick_if_missing_client|required_mod_ids|handshake_timeout_seconds') { throw 'The release must not require or inspect a client modpack.' }
Write-Host 'Narcotics client-mod requirement retirement validation passed.'
