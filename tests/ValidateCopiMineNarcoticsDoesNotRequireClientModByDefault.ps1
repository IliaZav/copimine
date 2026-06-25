$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
if ($config -notmatch 'require_client_mod: false') { throw 'Client mod must not be required by default.' }
Write-Host 'Narcotics default optional client validation passed.'