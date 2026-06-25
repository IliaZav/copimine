$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root 'resourcepacks\src\assets\copimine\textures\item\narcotics'
$required = @('feta.png','kola.png','girion.png','sbp.png','sos.png','drun.png','chups.png','borshevik.png','zhuzevo.png')
$missing = $required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $base $_)) }
if ($missing.Count -gt 0) { throw ("Missing narcotics item textures: " + ($missing -join ', ')) }
Write-Host 'Narcotics item texture source validation passed.'
