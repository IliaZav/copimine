$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$path = Join-Path $root 'resourcepacks\LICENSES_RESOURCEPACK.md'
if (-not (Test-Path -LiteralPath $path)) {
  throw 'ValidateCopiMineResourcePackLicenses failed: LICENSES_RESOURCEPACK.md is missing.'
}
$text = Get-Content -Raw -Encoding UTF8 $path
foreach ($marker in @('CopiMine', 'build-resourcepack.py')) {
  if ($text -notmatch [regex]::Escape($marker)) {
    throw "ValidateCopiMineResourcePackLicenses failed: missing marker $marker"
  }
}
Write-Host 'ValidateCopiMineResourcePackLicenses passed.'
