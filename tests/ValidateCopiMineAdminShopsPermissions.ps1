$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifacts = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('copimine.artifacts.shop.create', 'copimine.artifacts.shop.remove', 'copimine.artifacts.admin.gift', 'isArtifactsAdmin')) {
  if ($artifacts -notmatch [regex]::Escape($marker)) { $errors.Add("Missing server-side permission marker: $marker") }
}
if ($errors.Count) { throw ("Admin shops permissions validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineAdminShopsPermissions passed.'
