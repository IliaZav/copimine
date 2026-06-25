$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$main = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
foreach ($marker in @('PrepareItemCraftEvent','isOfficialFinishedItem','setResult(null)','case CRAFTING, WORKBENCH, CRAFTER')) {
  if ($main -notmatch [regex]::Escape($marker)) { throw "Craft blocking marker missing: $marker" }
}
Write-Host 'No crafting from finished items validation passed.'
