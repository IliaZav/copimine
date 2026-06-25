$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($effect in @('DESATURATE','COLOR_CONVOLVE','SCAN_PINCUSHION','GREEN_NOISE','INVERT','WOBBLE','BLOBS','PENCIL','CHAOS')) {
  if ($runtime -notmatch [regex]::Escape($effect)) { throw "Fallback effect missing: $effect" }
}
Write-Host 'Narcotics server particle fallback validation passed.'