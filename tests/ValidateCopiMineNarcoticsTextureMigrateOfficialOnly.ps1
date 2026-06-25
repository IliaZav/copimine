$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$factory = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java')
foreach ($marker in @(
  'texture migrate <online|nearby>',
  'migrateOfficialItems',
  '"RP_NARCOTIC"',
  'resolveOfficialLoose'
)) {
  if (($main + $factory) -notmatch [regex]::Escape($marker)) {
    throw "Official-only texture migrate marker missing: $marker"
  }
}
Write-Host 'Narcotics texture migrate official-only validation passed.'
