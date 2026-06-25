$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\models_manifest.json'
$json = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
$seen = @{}
$dupes = [System.Collections.Generic.List[string]]::new()

foreach ($item in $json.items) {
  $cmd = [string]$item.custom_model_data
  if ($seen.ContainsKey($cmd)) {
    $dupes.Add("$cmd => $($seen[$cmd]) and $($item.id)")
  } else {
    $seen[$cmd] = $item.id
  }
}

if ($dupes.Count -gt 0) {
  throw ("ValidateCopiMineCustomModelDataNoDuplicates failed:`n - " + ($dupes -join "`n - "))
}

Write-Host 'ValidateCopiMineCustomModelDataNoDuplicates passed.'
