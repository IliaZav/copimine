$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sources = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\item_texture_sources.json') | ConvertFrom-Json
$errors = [System.Collections.Generic.List[string]]::new()
$ids = @($sources.items | ForEach-Object { [string]$_.id })
if ($ids.Count -ne 40 -or @($ids | Select-Object -Unique).Count -ne 40) { $errors.Add('Texture source mapping must contain 40 unique custom-textured catalog ids including the retired compass tombstone; the vanilla shield is intentionally excluded.') }
foreach ($row in @($sources.items)) {
  if ([string]$row.source_path -match '\*' -and -not $row.frame_count -and $row.source_group -ne 'Generated') { $errors.Add("Wildcard source without frame_count: $($row.id)") }
  if ($row.frame_count -and [int]$row.frame_count -notin @(32,64)) { $errors.Add("Unexpected frame count for $($row.id)") }
  if ($row.catalog -eq 'AR' -and $row.source_group -notin @('No_Donate', 'Generated')) { $errors.Add("Wrong archive group for AR item $($row.id)") }
  if ($row.catalog -eq 'ADMIN_ONLY' -and $row.source_group -ne 'User_Supplied') { $errors.Add("Wrong source group for admin-only item $($row.id)") }
  if ($row.catalog -eq 'DONATION' -and $row.source_group -notin @('Donate', 'Generated')) { $errors.Add("Wrong archive group for donation item $($row.id)") }
}
if (@($sources.unassigned_archive_assets).Count -lt 1) { $errors.Add('Unassigned archive assets must be documented explicitly.') }
if ($errors.Count) { throw ("Custom item archive coverage failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineCustomItemArchiveCoverage passed.'
