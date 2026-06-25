$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\src\assets\copimine\manifests\block_visuals_manifest.json'
$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @('ATM','POLLING_STATION','TAX_OFFICE','ARTIFACT_SHOP','12002','14002','14003','14004')) {
  if ($manifest -notmatch [regex]::Escape($marker)) {
    $errors.Add("Block visuals manifest missing marker: $marker")
  }
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineResourcePackBlockVisualManifest failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineResourcePackBlockVisualManifest passed.'
