$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
if ($main -match [regex]::Escape('resetNarcoticsState().join()')) { throw 'Reset must not block on .join().' }
foreach ($marker in @('resetNarcoticsState().whenComplete','Bukkit.getScheduler().runTask(this')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Async reset callback marker missing: $marker" }
}
Write-Host 'Async reset validation passed.'
