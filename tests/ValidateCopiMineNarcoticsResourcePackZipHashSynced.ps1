$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$zipPath = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
$shaPath = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.sha1'
$serverProps = Join-Path $root 'minecraft\server\server.properties'
if (-not (Test-Path $zipPath)) { throw "Missing zip: $zipPath" }
if (-not (Test-Path $shaPath)) { throw "Missing sha1 file: $shaPath" }
if (-not (Test-Path $serverProps)) { throw "Missing server.properties: $serverProps" }
$zipSha = (Get-FileHash -Algorithm SHA1 -LiteralPath $zipPath).Hash.ToLowerInvariant()
$savedSha = (Get-Content -Raw -Encoding UTF8 $shaPath).Trim().ToLowerInvariant()
if ($zipSha -ne $savedSha) { throw "ZIP hash and CopiMineResourcePack.sha1 differ: $zipSha vs $savedSha" }
$props = Get-Content -Raw -Encoding UTF8 $serverProps
if ($props -notmatch [regex]::Escape("resource-pack-sha1=$zipSha")) {
  throw 'server.properties resource-pack-sha1 is not synchronized with the narcotics resource pack zip.'
}
Write-Host 'Narcotics resource pack zip hash is synchronized.'
