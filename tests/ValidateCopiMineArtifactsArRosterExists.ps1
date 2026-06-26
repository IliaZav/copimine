$ErrorActionPreference = "Stop"

$itemsFile = "D:\Desktop\Copimine\opt\copimine\copimine-artifacts\items.yml"
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

$requiredPrices = @(
  "price_ar: 2500",
  "price_ar: 2200",
  "price_ar: 1300",
  "price_ar: 1800",
  "price_ar: 5000",
  "price_ar: 9999"
)

foreach ($price in $requiredPrices) {
  if ($content -notmatch [regex]::Escape($price)) {
    throw "Missing AR artifact price marker: $price"
  }
}

Write-Host "PASS"
