$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
if ($config -notmatch '(?m)^\s*enabled:\s*true\s*$') { throw 'Optional client bridge must remain enabled for visual capabilities.' }
foreach ($marker in @('require_client_mod:', 'kick_if_missing_client:', 'required_mod_ids:', 'handshake_timeout_seconds:')) {
  if ($config -match [regex]::Escape($marker)) { throw "Retired modpack-enforcement config marker remains: $marker" }
}
foreach ($marker in @('enforceClientModpack', 'onAuthCommandBeforeModpack', 'kickPlayer(')) {
  if ($bridge -match [regex]::Escape($marker)) { throw "Retired client-mod gate marker remains: $marker" }
}
Write-Host 'Client bridge modpack enforcement retirement validation passed.'
