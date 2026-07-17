$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\models_manifest.json') | ConvertFrom-Json
$seen = @{}
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($row in @($manifest.items)) {
  $key = "{0}:{1}" -f ([string]$row.base_material).ToUpperInvariant(), [int]$row.custom_model_data
  if ($seen.ContainsKey($key)) { $errors.Add("Duplicate custom model key ${key}: $($seen[$key]) and $($row.id)") }
  $seen[$key] = [string]$row.id
  if ([int]$row.custom_model_data -le 0) { $errors.Add("Non-positive model data for $($row.id)") }
}
if ($errors.Count) { throw ("Custom model data validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineCustomItemModelDataUnique passed.'
