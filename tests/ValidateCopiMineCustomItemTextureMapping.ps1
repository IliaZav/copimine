$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcesPath = Join-Path $root 'resourcepacks\item_texture_sources.json'
$manifestPath = Join-Path $root 'resourcepacks\models_manifest.json'
$catalogPath = Join-Path $root 'copimine-artifacts\items.yml'
$sources = Get-Content -Raw -Encoding UTF8 $sourcesPath | ConvertFrom-Json
$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
$catalog = Get-Content -Raw -Encoding UTF8 $catalogPath
$required = @(
  'zmei_gorynych','smena_bez_perekura_pickaxe','lesnoy_bespredel_axe','kopatel_transhey_shovel','fermer_bez_sna_hoe',
  'dezhurniy_argument_sword','vechniy_razgon_firework','treasurer_chestplate','copimine_miner_pickaxe','craftsman_hammer',
  'eternal_totem','kozyrny_tuz_pozdnyakova',
  'batin_remen_sudnogo_dnya','nu_ty_i_nakopal_blyat_pickaxe','kosa_nalogovoy_inspekcii','kaska_prorab_huev',
  'mne_pohuy_ya_v_tanke_vest','ne_segodnya_suka_shield','ya_esche_ne_vse_isportil_totem','pohuy_na_debaffy_amulet',
  'vremya_platit_nalogi_clock','gde_moy_lut_blyat_compass'
)
$errors = [System.Collections.Generic.List[string]]::new()
$rows = @($sources.items)
$manifestRows = @($manifest.items)
$rowById = @{}
foreach ($row in $rows) { $rowById[[string]$row.id] = $row }
$manifestByKey = @{}
foreach ($row in $manifestRows) {
  $key = "{0}:{1}" -f ([string]$row.base_material).ToUpperInvariant(), [int]$row.custom_model_data
  if ($manifestByKey.ContainsKey($key)) { $errors.Add("Duplicate manifest key $key") }
  $manifestByKey[$key] = $row
}
foreach ($id in $required) {
  if ($catalog -notmatch [regex]::Escape($id)) { $errors.Add("Catalog is missing $id") }
  if (-not $rowById.ContainsKey($id)) { $errors.Add("Texture source mapping is missing $id"); continue }
  $row = $rowById[$id]
  if ([int]$row.custom_model_data -le 0) { $errors.Add("$id has non-positive custom model data") }
  if ($row.catalog -eq 'AR' -and $row.source_group -ne 'No_Donate') { $errors.Add("AR item $id must come from No_Donate") }
  if ($row.catalog -eq 'ADMIN_ONLY' -and $row.source_group -ne 'User_Supplied') { $errors.Add("Admin-only item $id must come from User_Supplied") }
  if ($row.catalog -eq 'DONATION' -and $row.source_group -ne 'Donate') { $errors.Add("Donation item $id must come from Donate") }
  $key = "{0}:{1}" -f ([string]$row.base_material).ToUpperInvariant(), [int]$row.custom_model_data
  if (-not $manifestByKey.ContainsKey($key)) { $errors.Add("Manifest key $key is missing for $id"); continue }
  $manifestRow = $manifestByKey[$key]
  if ([string]$manifestRow.id -ne $id) { $errors.Add("Manifest key $key points to $($manifestRow.id), expected $id") }
  $modelRef = ([string]$manifestRow.model) -split ':', 2
  $textureRef = ([string]$manifestRow.texture) -split ':', 2
  $model = Join-Path $root ('resourcepacks\src\assets\' + $modelRef[0] + '\models\' + $modelRef[1] + '.json')
  $texture = Join-Path $root ('resourcepacks\src\assets\' + $textureRef[0] + '\textures\' + $textureRef[1] + '.png')
  if (-not (Test-Path $model)) { $errors.Add("Missing model file for $id") }
  if (-not (Test-Path $texture)) { $errors.Add("Missing texture file for $id") }
  if ($row.frame_count) {
    $meta = "$texture.mcmeta"
    if (-not (Test-Path $meta)) { $errors.Add("Missing animation metadata for $id") }
  }
}
if ($rows.Count -ne $required.Count) { $errors.Add("Expected $($required.Count) source rows, found $($rows.Count)") }
if ($errors.Count) { throw ("Custom item texture mapping failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineCustomItemTextureMapping passed.'
