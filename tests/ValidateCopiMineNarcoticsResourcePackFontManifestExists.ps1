$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$file = Join-Path $root 'resourcepacks\src\assets\copimine\font\narcotics_overlay.json'
if (-not (Test-Path $file)) { throw 'narcotics_overlay.json is missing.' }
Write-Host 'Resource pack font manifest validation passed.'