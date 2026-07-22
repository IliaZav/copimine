$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$paths = @(
  (Join-Path $root 'copimine-artifacts\items.yml'),
  (Join-Path $root 'resourcepacks\models_manifest.json'),
  (Join-Path $root 'resourcepacks\item_texture_sources.json')
)

foreach ($path in $paths) {
  $content = Get-Content -Raw -Encoding UTF8 $path
  if ($content -match 'ya_esche_ne_vse_isportil_totem|BROKEN_TOTEM|20007|eternal_totem') {
    throw "Retired donation totem reference remains in $path"
  }
}

$artifactSource = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
if ($artifactSource -match 'INFINITE_TOTEM|resurrect_infinite_totem|restoreInfiniteTotem') {
  throw 'Infinite donation totem runtime support is still enabled.'
}

$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\item_texture_sources.json') | ConvertFrom-Json
if (@($source.items | Where-Object { $_.id -eq 'ya_esche_ne_vse_isportil_totem' }).Count -ne 0) {
  throw 'Retired donation totem is still mapped to a texture source.'
}

Write-Host 'Donation totem removal contract OK'
