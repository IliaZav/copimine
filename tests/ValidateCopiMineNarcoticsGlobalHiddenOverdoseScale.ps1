$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\use\OverdoseService.java')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
foreach ($marker in @('currentScale','overdoseThreshold','usageWindowSeconds','PlayerState','zhuzevoForcesOverdose')) {
  if (($source + $config) -notmatch [regex]::Escape($marker)) { throw "Hidden overdose scale marker missing: $marker" }
}
foreach ($forbidden in @('sendMessage("Передозировка:','bossbar overdose','sidebar overdose')) {
  if ($source -match [regex]::Escape($forbidden)) { throw "Player-visible overdose scale leak found: $forbidden" }
}
Write-Host 'Hidden overdose scale validation passed.'
