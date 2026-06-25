$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$licenses = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\LICENSES_RESOURCEPACK.md')
$thirdParty = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\THIRD_PARTY_NARCOTICS_VISUALS.md')
if ($licenses -notmatch 'self-generated|generated inside the CopiMine project|generated inside the project') {
  throw 'LICENSES_RESOURCEPACK.md does not clearly state self-generated narcotics assets.'
}
if ($thirdParty -notmatch 'License: CC0|CC0') {
  throw 'THIRD_PARTY_NARCOTICS_VISUALS.md must document reviewed permissive licenses.'
}
Write-Host 'Narcotics shader asset license validation passed.'
