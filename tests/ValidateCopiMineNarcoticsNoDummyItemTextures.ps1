$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = Get-ChildItem (Join-Path $root 'resourcepacks\src\assets\copimine\textures\item\narcotics') -Filter *.png
if ($files.Count -lt 9) { throw 'Not all narcotics item textures exist.' }
foreach ($file in $files) {
  if ($file.Length -lt 200) { throw "Item texture still looks dummy or too small: $($file.Name) ($($file.Length) bytes)" }
}
Write-Host 'Narcotics item textures are not dummy placeholders.'
