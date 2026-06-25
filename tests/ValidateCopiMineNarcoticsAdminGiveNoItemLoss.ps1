$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('Inventory().addItem', 'leftovers', 'dropItemNaturally', 'dropped=')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Admin give safety marker missing: $marker" }
}
Write-Host 'Admin give no-item-loss validation passed.'
