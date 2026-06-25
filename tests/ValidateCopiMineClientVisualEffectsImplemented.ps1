$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$code = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'CopiMineClient\src\main\java\me\copimine\client\ClientVisualManager.java')
foreach ($effect in @('DESATURATE','COLOR_CONVOLVE','SCAN_PINCUSHION','GREEN_NOISE','INVERT','WOBBLE','BLOBS','PENCIL','CHAOS')) {
  if ($code -notmatch [regex]::Escape($effect)) { throw "Client visual effect missing: $effect" }
}
Write-Host 'CopiMineClient visual effect implementation validation passed.'
