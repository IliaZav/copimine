$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$bridge = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')
if ($config -notmatch 'client_gate:' -or $config -notmatch 'required: true' -or $config -notmatch 'ready_timeout_seconds: 15') { throw 'Config missing the required 15-second client gate contract.' }
foreach ($marker in @('clientGateRequired','ClientReadyAdmission.PendingClientAdmission','handleAdmissionTimeout','joinAttemptId','CLIENT_READY_TIMEOUT')) {
  if ($bridge -notmatch [regex]::Escape($marker)) { throw "Client READY gate marker missing: $marker" }
}
if ($bridge -notmatch 'STALE_JOIN_ATTEMPT_IGNORED') { throw 'Stale timeout diagnostics are required.' }
Write-Host 'Client READY grace-timeout and stale-attempt validation passed.'
