$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$zip = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
if (-not (Test-Path $zip)) { throw 'Built resource pack zip is missing.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
try {
  foreach ($entry in @('assets/copimine/font/narcotics_overlay.json','assets/copimine/textures/gui/narcotics/desaturate_overlay.png','assets/copimine/textures/gui/narcotics/chaos_overlay.png')) {
    if (-not ($archive.Entries | Where-Object { $_.FullName -eq $entry })) { throw "Zip missing $entry" }
  }
} finally { $archive.Dispose() }
Write-Host 'Resource pack zip overlay asset validation passed.'