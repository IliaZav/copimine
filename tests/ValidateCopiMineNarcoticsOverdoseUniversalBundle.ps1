$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java')
foreach ($marker in @(
  'new ConfiguredEffect("DARKNESS", 0, 45)',
  'new ConfiguredEffect("WEAKNESS", 0, 300)',
  'new ConfiguredEffect("INSTANT_DAMAGE", 0, 1)',
  'new ConfiguredEffect("NAUSEA", 2, 180)',
  'new ConfiguredEffect("MINING_FATIGUE", 4, 300)'
)) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Universal overdose marker missing: $marker" }
}
Write-Host 'ValidateCopiMineNarcoticsOverdoseUniversalBundle passed.'
