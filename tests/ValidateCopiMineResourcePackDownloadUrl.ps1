$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$builder = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\build-resourcepack.py')
if ($builder -notmatch 'DEFAULT_RESOURCE_PACK_URL\s*=\s*r?"http\\://copimine\.ru\\:18080/resourcepacks/CopiMineResourcePack\.zip"') {
  throw 'Resource-pack builder must use the public copimine.ru download URL.'
}
if ($builder -match 'DEFAULT_RESOURCE_PACK_URL[^\r\n]*admin\.copimine\.ru') {
  throw 'Retired admin.copimine.ru resource-pack URL is still configured.'
}
Write-Host 'Resource-pack download URL validation passed.'
