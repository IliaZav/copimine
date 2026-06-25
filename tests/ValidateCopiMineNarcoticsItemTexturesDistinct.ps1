$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = Get-ChildItem (Join-Path $root 'resourcepacks\src\assets\copimine\textures\item\narcotics') -Filter *.png
$hashes = @{}
foreach ($file in $files) {
  $hash = (Get-FileHash $file.FullName -Algorithm SHA1).Hash
  if ($hashes.ContainsKey($hash)) {
    throw "Duplicate narcotics item texture content detected: $($file.Name) and $($hashes[$hash])"
  }
  $hashes[$hash] = $file.Name
}
Write-Host 'Narcotics item textures are distinct.'
