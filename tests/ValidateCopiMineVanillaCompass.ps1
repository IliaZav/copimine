$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java')
$items = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts/items.yml')
if ($source -match 'setCompassTarget\(') {
  throw 'Artifact compass must not change the player-wide vanilla compass target.'
}
if ($source -match 'activateLootCompass|COMPASS_COOLDOWN_SECONDS|MAX_COMPASS_TELEPORT_DISTANCE|keyCompassCooldownUntil|case "LOOT_COMPASS"') {
  throw 'Retired compass runtime handlers or cooldown markers are still present.'
}
if ($source -notmatch 'isRetiredArtifact' -or $source -notmatch 'gde_moy_lut_blyat_compass') {
  throw 'Retired compass guard is missing.'
}
if ($items -notmatch '(?ms)item-id:\s*gde_moy_lut_blyat_compass.*?enabled:\s*false') {
  throw 'Retired compass must remain disabled in the catalog.'
}
Write-Host 'Retired compass isolation validation passed.'
