$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$service = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
if ($service -notmatch '"no-ack"') { throw 'ClientVisualEffectService must mark no-ack fallback reason.' }
if ($runtime -notmatch 'applyServerFallbackRoute') { throw 'VisualRuntimeService must switch to server fallback after client route failure.' }
Write-Host 'Server fallback-after-no-ack validation passed.'
