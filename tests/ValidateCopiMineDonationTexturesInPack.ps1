$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$zipPath = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
if (-not (Test-Path -LiteralPath $zipPath)) { throw 'Built resource pack is missing.' }
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\models_manifest.json') | ConvertFrom-Json
$sources = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\item_texture_sources.json') | ConvertFrom-Json
$donationIds = @($sources.items | Where-Object catalog -eq 'DONATION' | ForEach-Object id)
$byKey = @{}
foreach ($row in @($manifest.items)) { $byKey[('{0}:{1}' -f ([string]$row.base_material).ToUpperInvariant(), [int]$row.custom_model_data)] = $row }
$archive = [IO.Compression.ZipFile]::OpenRead($zipPath)
try {
  $entries = @{}
  foreach ($entry in $archive.Entries) { $entries[$entry.FullName] = $entry }
  foreach ($id in $donationIds) {
    $source = @($sources.items | Where-Object id -eq $id)[0]
    $key = '{0}:{1}' -f ([string]$source.base_material).ToUpperInvariant(), [int]$source.custom_model_data
    if (-not $byKey.ContainsKey($key)) { throw "Donation manifest override is missing: $id" }
    $row = $byKey[$key]
    $modelRef = ([string]$row.model) -split ':', 2
    $textureRef = ([string]$row.texture) -split ':', 2
    foreach ($asset in @(
      ('assets/{0}/models/{1}.json' -f $modelRef[0], $modelRef[1]),
      ('assets/{0}/textures/{1}.png' -f $textureRef[0], $textureRef[1])
    )) {
      if (-not $entries.ContainsKey($asset) -or $entries[$asset].Length -le 0) { throw "Donation asset missing from resource pack: $id -> $asset" }
    }
  }
} finally { $archive.Dispose() }
Write-Host "Donation texture pack validation passed: $($donationIds.Count) items."
