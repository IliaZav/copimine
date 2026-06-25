$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sources = @(
  (Join-Path $root 'copimine-admin-plugin\src\me\copimine\ultimateplus\CopiMineUltimateAdminPlus.java'),
  (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'),
  (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
)
$errors = New-Object System.Collections.Generic.List[string]
foreach ($path in $sources) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing source: $path"); continue }
  $text = Get-Content -Raw -Encoding UTF8 $path
  foreach ($bad in @('PSQLException','SQLException:','getClass().getSimpleName() + ": " +','ATM error:','GUI: "+ex.getClass')) {
    if ($text.Contains($bad)) { $errors.Add(("Raw SQL/Java error marker in {0}: {1}" -f $path, $bad)) }
  }
}
$admin = Get-Content -Raw -Encoding UTF8 $sources[0]
foreach ($marker in @('getLogger().warning("gui: "+ex)','getLogger().warning("cmd: "+e)','safeErr')) {
  if (-not $admin.Contains($marker)) { $errors.Add("Missing safe error/log marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("GUI raw SQL error validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'GUI raw SQL error validation passed.'
