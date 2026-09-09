$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$items = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\items.yml')

if ($source -match 'LOOT_COMPASS|activateLootCompass|keyCompassCooldownUntil|legacyPointCompassToLastDeath') {
  throw 'Retired donation compass runtime code is still present.'
}
if ($items -match 'gde_moy_lut_blyat_compass') {
  throw 'Retired donation compass remains in the catalog.'
}
if ($source -notmatch 'Material\.RECOVERY_COMPASS') {
  throw 'Vanilla recovery compass normalization must remain available.'
}

Write-Host 'Retired donation compass removal validation passed.'
