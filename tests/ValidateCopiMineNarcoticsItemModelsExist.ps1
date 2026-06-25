$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root 'resourcepacks\src\assets\copimine\models\item'
$required = @('feta.json','kola.json','girion.json','sbp.json','sos.json','drun.json','chups.json','borshevik.json','zhuzevo.json')
$missing = $required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $base $_)) }
if ($missing.Count -gt 0) { throw ("Missing narcotics item models: " + ($missing -join ', ')) }
Write-Host 'Narcotics item model source validation passed.'
