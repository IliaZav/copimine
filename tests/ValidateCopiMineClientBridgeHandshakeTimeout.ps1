$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
if ($config -notmatch 'handshake_timeout_seconds' -or $config -notmatch 'required_client_mod_id: copimineclient' -or $config -notmatch 'required_client_mod_version:') {
  throw 'Config must identify the required client mod, exact version, and handshake timeout.'
}
if ($bridge -notmatch 'pendingHandshakes' -or $bridge -notmatch 'encodeClientModChallenge' -or $bridge -notmatch 'runTaskLater' -or $bridge -notmatch 'player\.kickPlayer\(') {
  throw 'Missing, timed-out, or incompatible CopiMineClient handshakes must be rejected.'
}
Write-Host 'Client bridge mandatory handshake timeout validation passed.'
