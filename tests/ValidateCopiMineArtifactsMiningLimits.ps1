$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()

if ($source -notmatch 'while \(!var4\.isEmpty\(\) && var6\.size\(\) < 19\)') {
  $errors.Add('Forester chain must cap additional blocks at 19 so the triggering block keeps the total at 20.')
}
if ($source -notmatch 'if \(!var1\.breakBlock\(var14\)\) \{\s*this\.chainedTreeBreaks\.remove\(var15\);\s*\}') {
  $errors.Add('Cancelled forester breaks must remove their chain marker.')
}
if ($source -notmatch 'if \(!player\.breakBlock\(target\)\) \{\s*this\.chainedTreeBreaks\.remove\(targetKey\);\s*\}') {
  $errors.Add('Cancelled 3x3 miner breaks must remove their chain marker.')
}

if ($errors.Count -gt 0) {
  throw ("Artifacts mining limit validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Artifacts mining limit validation passed.'
