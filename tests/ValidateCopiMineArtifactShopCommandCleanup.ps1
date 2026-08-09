$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$remove = [regex]::Match($source, '(?s)else if \("remove"\.equalsIgnoreCase\(var2\[0\]\)\).*?(?=\r?\n\s*\} else if \("rename"\.equalsIgnoreCase\(var2\[0\]\)\))')
if (-not $remove.Success) {
    throw 'Could not locate the direct shop remove command handler.'
}

if ($remove.Value -notmatch 'cleanupShopTitleDisplay\(') {
    throw 'The direct /cmartifacts shop remove command must remove its SHOP_TITLE_DISPLAY entity.'
}

Write-Host 'Artifact shop command cleanup contract OK'
