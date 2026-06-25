$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$files = Get-ChildItem -Recurse (Join-Path $root 'resourcepacks\src\assets\copimine') -Include *.json,*.mcmeta | Select-Object -ExpandProperty FullName
foreach ($file in $files) {
  $content = Get-Content -Raw -Encoding UTF8 $file
  if ($content -match 'https?://') {
    throw "Hotlinking is not allowed in source asset file: $file"
  }
}
Write-Host 'Narcotics no-hotlinking validation passed.'
