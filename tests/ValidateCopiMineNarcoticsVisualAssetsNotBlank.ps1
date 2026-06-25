$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = Get-ChildItem (Join-Path $root 'resourcepacks\src\assets\copimine\textures\gui\narcotics') -Filter *.png
foreach ($file in $files) {
  if ($file.Length -lt 1000) { throw "Overlay asset looks blank or too small: $($file.Name) ($($file.Length) bytes)" }
}
Write-Host 'Visual overlay assets are not blank.'
