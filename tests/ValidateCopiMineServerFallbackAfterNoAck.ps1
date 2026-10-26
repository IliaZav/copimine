$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$service = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($service -notmatch '"no-ack"') { throw 'ClientVisualEffectService must mark no-ack fallback reason.' }
if ($runtime -notmatch 'applyServerFallback') { throw 'VisualRuntimeService must present visuals through the server particle fallback.' }
foreach ($marker in @('sendVisualStart','sendVisualRefresh')) {
  if ($runtime -match [regex]::Escape($marker)) { throw "Retired narcotics shader route must not be started: $marker" }
}
Write-Host 'Server fallback-after-no-ack validation passed.'