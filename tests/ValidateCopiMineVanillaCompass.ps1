$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java')
if ($source -match 'setCompassTarget\(') {
  throw 'Artifact compass must not change the player-wide vanilla compass target.'
}
if ($source -notmatch 'setLodestone\(') {
  throw 'Donation compass no longer writes its own lodestone target.'
}
Write-Host 'Vanilla compass isolation validation passed.'
