$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('/cmnarcotics visuals test <', 'visualRuntime.apply(target, effectId, seconds, false)')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Visual test command marker missing: $marker" }
}
Write-Host 'Visuals test command applies an effect path.'
