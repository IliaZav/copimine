$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$service = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java')
foreach ($needle in @('RETRY_INTERVAL_MILLIS','pendingAcks','handleMessage','ReportKind.ACK','ackDeadlineMillis()','correlations.accept(','no-ack')) {
  if ($service -notmatch [regex]::Escape($needle)) { throw "Server visual ACK marker missing: $needle" }
}
Write-Host 'Server visual ACK-required validation passed.'
