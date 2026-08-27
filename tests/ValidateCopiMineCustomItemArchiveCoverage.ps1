$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sources = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\item_texture_sources.json') | ConvertFrom-Json
$catalogText = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\items.yml')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\models_manifest.json') | ConvertFrom-Json
$errors = [System.Collections.Generic.List[string]]::new()

function Get-Scalar([string]$block, [string[]]$names) {
  foreach ($name in $names) {
    $match = [regex]::Match($block, "(?m)^\s*$([regex]::Escape($name)):\s*(?<value>[^#\r\n]+)")
    if ($match.Success) { return $match.Groups['value'].Value.Trim().Trim('"').Trim("'") }
  }
  return $null
}

$catalogRows = [System.Collections.Generic.List[object]]::new()
$mainBlocks = [regex]::Matches($catalogText, '(?ms)^  - id: .*?(?=^  - id:|^donation-catalog:|\z)')
foreach ($match in $mainBlocks) {
  $block = $match.Value
  $id = ([regex]::Match($block, '(?m)^\s*-\s+id:\s*(?<value>[^#\r\n]+)')).Groups['value'].Value.Trim().Trim('"').Trim("'")
  $modelData = [int](Get-Scalar $block @('custom_model_data', 'custom-model-data'))
  $allow = Get-Scalar $block @('custom-texture-mode-allowed')
  $catalog = if ((Get-Scalar $block @('source')) -eq 'ADMIN_ONLY') { 'ADMIN_ONLY' } else { 'AR' }
  $catalogRows.Add([pscustomobject]@{ Id = $id; Material = (Get-Scalar $block @('material', 'base-material')); ModelData = $modelData; Allowed = $allow; Catalog = $catalog })
}
$donationBlocks = [regex]::Matches($catalogText, '(?ms)^    - item-id: .*?(?=^    - item-id:|\z)')
foreach ($match in $donationBlocks) {
  $block = $match.Value
  $catalogRows.Add([pscustomobject]@{
      Id = ([regex]::Match($block, '(?m)^\s*-\s+item-id:\s*(?<value>[^#\r\n]+)')).Groups['value'].Value.Trim().Trim('"').Trim("'")
      Material = Get-Scalar $block @('base-material', 'material')
      ModelData = [int](Get-Scalar $block @('custom-model-data', 'custom_model_data'))
      Allowed = Get-Scalar $block @('custom-texture-mode-allowed')
      Catalog = 'DONATION'
    })
}

$customCatalogRows = @($catalogRows | Where-Object {
    $isDisabledVanilla = $false
    if (-not [string]::IsNullOrWhiteSpace([string]$_.Allowed)) {
      $isDisabledVanilla = ([string]$_.Allowed).ToLowerInvariant() -in @('false', '0', 'no', 'off') -and $_.ModelData -eq 0
    }
    -not $isDisabledVanilla
  })
$catalogById = @{}
foreach ($row in $customCatalogRows) {
  if ([string]::IsNullOrWhiteSpace($row.Id)) { $errors.Add('Catalog contains an item without an id.'); continue }
  if ($catalogById.ContainsKey($row.Id)) { $errors.Add("Duplicate catalog id: $($row.Id)") }
  $catalogById[$row.Id] = $row
  if ($row.ModelData -le 0) { $errors.Add("Custom catalog item has non-positive custom model data: $($row.Id)") }
}

$sourceRows = @($sources.items)
$ids = @($sourceRows | ForEach-Object { [string]$_.id })
if ($ids.Count -ne @($ids | Select-Object -Unique).Count) { $errors.Add('Texture source mapping contains duplicate ids.') }
if ($ids.Count -ne $catalogById.Count -or @($ids | Where-Object { -not $catalogById.ContainsKey($_) }).Count -gt 0 -or @($catalogById.Keys | Where-Object { $_ -notin $ids }).Count -gt 0) {
  $errors.Add('item_texture_sources.json must contain exactly one row per current custom-textured catalog item.')
}

$manifestPairs = @{}
$manifestById = @{}
foreach ($row in @($manifest.items)) {
  $pair = "$([string]$row.base_material.ToUpperInvariant()):$([int]$row.custom_model_data)"
  if ($manifestPairs.ContainsKey($pair)) { $errors.Add("Duplicate resource-pack material/model pair: $pair") }
  $manifestPairs[$pair] = $true
  if ($row.id) { $manifestById[[string]$row.id] = $row }
}

foreach ($row in $sourceRows) {
  $id = [string]$row.id
  if (-not $catalogById.ContainsKey($id)) { continue }
  $catalogRow = $catalogById[$id]
  if ([string]::IsNullOrWhiteSpace([string]$row.source_path)) { $errors.Add("Missing source_path for $id") }
  if ([string]$row.base_material -ne [string]$catalogRow.Material -or [int]$row.custom_model_data -ne [int]$catalogRow.ModelData) {
    $errors.Add("Source mapping does not match catalog material/model data: $id")
  }
  if ($row.frame_count -and [int]$row.frame_count -notin @(32, 64)) { $errors.Add("Unexpected frame count for $id") }
  switch ($catalogRow.Catalog) {
    'AR' { if ([string]$row.source_group -notin @('No_Donate', 'Generated')) { $errors.Add("AR item has invalid source group $($row.source_group): $id") } }
    'ADMIN_ONLY' { if ([string]$row.source_group -ne 'User_Supplied') { $errors.Add("Admin-only item has invalid source group: $id") } }
    'DONATION' { if ([string]$row.source_group -ne 'Donate') { $errors.Add("Donation item has invalid source group: $id") } }
  }
  $pair = "$([string]$catalogRow.Material.ToUpperInvariant()):$([int]$catalogRow.ModelData)"
  if (-not $manifestPairs.ContainsKey($pair)) { $errors.Add("Missing resource-pack manifest override: $id") }
  if ($manifestById.ContainsKey($id)) {
    $manifestRow = $manifestById[$id]
    if ([int]$manifestRow.custom_model_data -ne [int]$catalogRow.ModelData -or [string]$manifestRow.base_material.ToUpperInvariant() -ne [string]$catalogRow.Material.ToUpperInvariant()) {
      $errors.Add("Manifest row does not match catalog material/model data: $id")
    }
  }
}
if ([int]$sources.mapping_version -ne 1 -or [string]$sources.source_archive_sha256 -notmatch '^[0-9A-Fa-f]{64}$') { $errors.Add('Texture source archive metadata is missing or malformed.') }
if (@($sources.unassigned_archive_assets).Count -lt 1) { $errors.Add('Unassigned archive assets must be documented explicitly.') }
if ($errors.Count) { throw ("Custom item archive coverage failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineCustomItemArchiveCoverage passed.'
