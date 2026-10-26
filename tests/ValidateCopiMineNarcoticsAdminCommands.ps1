$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('handleGive','handleReload','handleResetState','handleClearOverdose','handleTexture','handleVisuals','handleSelfCheck','handleInfo','handleSetWeight','handleSetThreshold','handleSetWindow','handleSetDuration')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Admin command handler missing: $marker" }
}
# Visuals follow consumption only: no runtime command may re-enable or re-mode them.
foreach ($removed in @('handleVisualMode','handleVisualEffect')) {
  if ($main -match [regex]::Escape("private boolean $removed")) { throw "Retired visual settings handler is back: $removed" }
}
Write-Host 'Admin command validation passed.'
