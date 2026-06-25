$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$json = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\resources\fabric.mod.json') | ConvertFrom-Json
if ($json.id -ne 'copimineclient') { throw 'fabric.mod.json id must be copimineclient.' }
if ($json.environment -ne 'client') { throw 'fabric.mod.json must be client-only.' }
if (-not $json.entrypoints.client) { throw 'fabric.mod.json missing client entrypoint.' }
Write-Host 'CopiMineClient fabric.mod.json validation passed.'
