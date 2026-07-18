$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)private CopiMineArtifacts\.CatalogItem authenticCatalogItem\(ItemStack var1, Player var2, String var3\) \{.*?(?=\r?\n\s*private ItemStack createOfficialItem)')

if (-not $method.Success) {
    throw 'Could not locate official artifact authenticity check.'
}

if ($method.Value -notmatch 'var1\.getAmount\(\)\s*!=\s*1') {
    throw 'Official artifacts must reject stacked unique items.'
}

if ($method.Value -notmatch 'var1\.getType\(\)\s*!=\s*var8\.material\(\)') {
    throw 'Official artifacts must verify that the item material matches its catalog entry.'
}

Write-Host 'Artifact authenticity hardening contract OK'
