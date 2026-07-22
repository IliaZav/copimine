$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$serverProperties = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'minecraft\server\server.properties')
$unpackInstaller = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'deploy\ubuntu\copimine_unpack_and_verify.sh')
$releaseInstaller = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'deploy\ubuntu\install_release.sh')
$backendSource = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\main.py')

if ($serverProperties -notmatch '(?m)^entity-broadcast-range-percentage=100\s*$') {
  throw 'entity-broadcast-range-percentage must be 100 so mobs remain visible across the configured tracking range.'
}
if ($serverProperties -notmatch '(?m)^view-distance=10\s*$') {
  throw 'view-distance must remain 10.'
}
if ($serverProperties -notmatch '(?m)^difficulty=hard\s*$') {
  throw 'difficulty must be hard.'
}
if ($serverProperties -notmatch '(?m)^simulation-distance=5\s*$') {
  throw 'simulation-distance must be 5.'
}
if ($unpackInstaller -notmatch '"entity-broadcast-range-percentage":\s*"100"') {
  throw 'The unpack installer must restore entity broadcast range to 100.'
}
if ($unpackInstaller -notmatch '"difficulty":\s*"hard"') {
  throw 'The unpack installer must restore hard difficulty.'
}
if ($releaseInstaller -notmatch "'entity-broadcast-range-percentage':\s*'100'") {
  throw 'The release installer must restore entity broadcast range to 100.'
}
if ($releaseInstaller -notmatch "'difficulty':\s*'hard'") {
  throw 'The release installer must restore hard difficulty.'
}
if ($backendSource -notmatch 'entity-broadcast-range-percentage"\s*,\s*"ok"\s*:\s*prop_int\("entity-broadcast-range-percentage",\s*100\)\s*>=\s*100') {
  throw 'Admin performance readiness must treat the full entity tracking range as valid for gameplay visibility.'
}

Write-Host 'Mob visibility validation passed: view distance is 10, simulation distance is 5, and entity tracking is not halved.'
