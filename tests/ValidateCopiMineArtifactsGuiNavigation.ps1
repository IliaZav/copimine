$ErrorActionPreference = 'Stop'
$text = Get-Content -Raw (Resolve-Path (Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'))
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @('back:main','back:category','back:detail','back:confirm','Назад','openMain(','openCategory(','openDetail(','openConfirm(','openPin(')) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing navigation marker: $marker") }
}
if ($errors.Count -gt 0) { throw ("Artifacts GUI navigation validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'Artifacts GUI navigation validation passed.'
