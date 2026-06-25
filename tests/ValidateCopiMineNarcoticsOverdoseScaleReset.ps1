$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java')
foreach ($marker in @('new PlayerState(state.playerUuid(), 0, state.lastConsumedAt(), now + duration','configService.overdoseWeightFor(definition)')) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Overdose scale reset marker missing: $marker" }
}
Write-Host 'Overdose scale reset validation passed.'
