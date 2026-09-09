$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
if ($config -notmatch 'require_client_mod: false') { throw 'Client mod must not be required by default.' }
if ($config -notmatch 'client_gate:\s*\r?\n\s*#.*\r?\n\s*required: true') { throw 'The compatibility gate must be explicit and separate from optional visual capability configuration.' }
Write-Host 'Narcotics default optional client validation passed.'
