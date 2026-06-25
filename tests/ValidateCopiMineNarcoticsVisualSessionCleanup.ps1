$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\visualruntime\VisualRuntimeService.java')
foreach ($marker in @('cleanupTasks','cancelTask(previousTask)','runTaskLater','session.untilMillis() <= System.currentTimeMillis()','clear(player)')) {
  if ($source -notmatch [regex]::Escape($marker)) { throw "Visual session cleanup marker missing: $marker" }
}
Write-Host 'Visual session cleanup validation passed.'
