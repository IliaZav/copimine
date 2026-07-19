$ErrorActionPreference = 'Stop'
$mixin = Get-Content (Join-Path $PSScriptRoot '..\CopiMineClient\src\main\java\me\copimine\client\mixin\ArmorFeatureRendererMixin.java') -Raw
$json = Get-Content (Join-Path $PSScriptRoot '..\CopiMineClient\src\main\resources\copimineclient.mixins.json') -Raw
$armor = Join-Path $PSScriptRoot '..\resourcepacks\src\assets\copimine\textures\models\armor'
foreach ($marker in @('ArmorFeatureRenderer', 'renderArmor', 'artifact_item_id', 'kaska_prorab_huev_layer_1', 'mne_pohuy_ya_v_tanke_layer_1', 'treasurer_chestplate', 'kozyrny_tuz_pozdnyakova')) {
  if ($mixin -notmatch [regex]::Escape($marker)) { throw "Missing custom armor marker: $marker" }
}
if ($json -notmatch 'ArmorFeatureRendererMixin') { throw 'Client mixin config does not register armor renderer override.' }
foreach ($file in @('kaska_prorab_huev_layer_1.png','mne_pohuy_ya_v_tanke_layer_1.png','kaznacheyskiy_layer_1.png','secret_items_layer_2.png')) {
  $path = Join-Path $armor $file
  if (-not (Test-Path $path)) { throw "Missing armor layer texture: $file" }
  if ((Get-Item $path).Length -le 100) { throw "Armor layer texture is empty: $file" }
}
Write-Host 'Client armor texture validation passed.'
