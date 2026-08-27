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

$expectedPrices = [ordered]@{
  smena_bez_perekura_pickaxe = 100
  lesnoy_bespredel_axe = 90
  kopatel_transhey_shovel = 30
  fermer_bez_sna_hoe = 50
  dezhurniy_argument_sword = 200
  vechniy_razgon_firework = 9999
}

foreach ($entry in $expectedPrices.GetEnumerator()) {
  $item = [regex]::Match($content, "(?ms)^  - id: $([regex]::Escape($entry.Key))\r?\n.*?(?=^  - id:|^donation-catalog:|\z)")
  if (-not $item.Success) { throw "Missing AR artifact roster item block: $($entry.Key)" }
  if ($item.Value -notmatch "(?m)^    price_ar:\s*$($entry.Value)\s*$") {
    throw "AR artifact price is not current for $($entry.Key): expected $($entry.Value)"
  }
}

Write-Host "PASS"
