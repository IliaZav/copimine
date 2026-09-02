$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java')
if ($source -match 'setCompassTarget\(') {
  throw 'Artifact compass must not change the player-wide vanilla compass target.'
}
if ($source -match 'LOOT_COMPASS|activateLootCompass|keyCompassCooldownUntil') {
  throw 'Retired donation compass runtime code must not be present.'
}
if ($source -notmatch 'Material\.RECOVERY_COMPASS') {
  throw 'Vanilla recovery compass support must remain available.'
}
Write-Host 'Vanilla compass isolation and donation compass retirement validation passed.'
