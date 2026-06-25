$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$errors = [System.Collections.Generic.List[string]]::new()

$files = @(
  'resourcepacks\src\assets\copimine\models\item\block\atm_terminal.json',
  'resourcepacks\src\assets\copimine\models\item\block\polling_station_marker.json',
  'resourcepacks\src\assets\copimine\models\item\block\tax_office_marker.json',
  'resourcepacks\src\assets\copimine\models\item\block\artifact_shop_marker.json',
  'resourcepacks\src\assets\copimine\textures\block\atm_terminal.png',
  'resourcepacks\src\assets\copimine\textures\block\polling_station_marker.png',
  'resourcepacks\src\assets\copimine\textures\block\tax_office_marker.png',
  'resourcepacks\src\assets\copimine\textures\block\artifact_shop_marker.png'
)

foreach ($file in $files) {
  if (-not (Test-Path (Join-Path $root $file))) {
    $errors.Add("Missing resource pack block visual file: $file")
  }
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineResourcePackModelsExist failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineResourcePackModelsExist passed.'
