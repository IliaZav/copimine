$ErrorActionPreference = 'Stop'
$items = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\items.yml'))
$java = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @(
  'WEAPON',
  'ARMOR',
  'TOOL',
  'RP',
  'dragon_punisher',
  'watch_blade',
  'treasurer_chestplate',
  'copimine_miner_pickaxe',
  'craftsman_hammer',
  'soonIcon',
  'Category.RP'
)) {
  if (($items + "`n" + $java) -notmatch [regex]::Escape($marker)) { $errors.Add("Missing category/item marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts categories validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts categories validation passed.'
