$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$doc = Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md'
if (-not (Test-Path -LiteralPath $doc)) { throw 'Missing THIRD_PARTY_NARCOTICS_VISUALS.md' }
Write-Host 'Narcotics third-party notice validation passed.'
