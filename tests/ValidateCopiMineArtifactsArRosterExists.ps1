$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$itemsFile = Join-Path $root 'copimine-artifacts\items.yml'
$content = Get-Content -Raw $itemsFile

$requiredIds = @(
  "smena_bez_perekura_pickaxe",
  "lesnoy_bespredel_axe",
  "kopatel_transhey_shovel",
  "fermer_bez_sna_hoe",
  "dezhurniy_argument_sword",
  "vechniy_razgon_firework"
)

foreach ($id in $requiredIds) {
  if ($content -notmatch [regex]::Escape($id)) {
    throw "Missing AR artifact roster item: $id"
  }
}

foreach ($id in $requiredIds) {
  $itemBlock = [regex]::Match($content, "(?ms)^\s*- id:\s*$([regex]::Escape($id))\s*$.*?(?=^\s*- id:|\z)")
  if (-not $itemBlock.Success -or $itemBlock.Value -notmatch '(?m)^\s*price_ar:\s*[0-9]+\s*$') {
    throw "Missing AR artifact price for roster item: $id"
  }
}

Write-Host "PASS"
