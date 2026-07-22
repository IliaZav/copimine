$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java')
if ($source -match 'setCompassTarget\(') {
  throw 'Artifact compass must not change the player-wide vanilla compass target.'
}
if ($source -notmatch 'rayTraceBlocks' -or $source -notmatch 'teleport\(') {
  throw 'Donation compass must trace and teleport along the look direction.'
}
Write-Host 'Vanilla compass isolation validation passed.'
