$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$current = [regex]::Match($source, '(?s)private CopiMineArtifacts\.Shop currentShop\(CopiMineArtifacts\.SessionState var1\) \{.*?(?=\r?\n\s*private )')
if (-not $current.Success) { throw 'currentShop method was not found.' }
if ($current.Value -notmatch 'equalsIgnoreCase\(var1\.shopId\)' -or $current.Value -notmatch 'return null;') {
    throw 'A stale shop session must fail closed and never fall back to another shop.'
}
$submit = [regex]::Match($source, '(?s)else if \("pin:submit"\.equals\(var3\)\).*?(?=\r?\n\s*\} else if|\r?\n\s*private )')
if (-not $submit.Success -or $submit.Value -notmatch 'currentShop\(var2\)' -or $submit.Value -notmatch 'closeInventory\(\)') {
    throw 'PIN submission must revalidate the selected shop and close a stale-shop session.'
}

Write-Host 'Artifacts purchase shop binding contract OK'
