$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\config\NarcoticsConfigService.java')
foreach ($marker in @(
  'VALID_TEXTURE_MODES',
  'VALID_VISUAL_MODES',
  'parseBoundedInt',
  '1, 1000',
  '1, 10000',
  '60, 86400',
  '10, 3600',
  'Unknown visual effect id',
  'throw new IllegalArgumentException("Unknown visual effect id: "'
)) {
  if (($main + $config) -notmatch [regex]::Escape($marker)) { throw "Command validation marker missing: $marker" }
}
Write-Host 'Command input validation markers present.'
