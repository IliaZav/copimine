$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$base = Join-Path $root 'resourcepacks\src\assets\copimine\textures\block'
$required = @(
  'atm_terminal.png',
  'polling_station_marker.png',
  'tax_office_marker.png',
  'artifact_shop_marker.png'
)
$missing = $required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $base $_)) }
if ($missing.Count -gt 0) {
  throw ("ValidateCopiMineResourcePackTexturesExist failed:`n - Missing textures: " + ($missing -join ', '))
}
Write-Host 'ValidateCopiMineResourcePackTexturesExist passed.'
