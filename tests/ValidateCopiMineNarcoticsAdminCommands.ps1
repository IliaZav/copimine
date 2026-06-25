$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('handleGive','handleReload','handleResetState','handleClearOverdose','handleTexture','handleVisuals','handleVisualMode','handleVisualEffect','handleSelfCheck','handleInfo','handleSetWeight','handleSetThreshold','handleSetWindow','handleSetDuration')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Admin command handler missing: $marker" }
}
Write-Host 'Admin command validation passed.'
