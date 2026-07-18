$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8

$list = [regex]::Match($source, '(?s)private void openAdminShopListAsync\(Player player\) \{.*?(?=\r?\n\s*private boolean hasSeniorShopPermission)')
if (-not $list.Success -or $list.Value -notmatch 'shop\.title\(\)' -or $list.Value -notmatch 'shop\.shopId\(\)') {
    throw 'The full shop list must display both the visible shop title and its id.'
}

$detail = [regex]::Match($source, '(?s)else if \(var3\.startsWith\("shop:detail:"\)\) \{.*?(?=\r?\n\s*\} else if \(var3\.startsWith\("shop:delete:ask:")')
if (-not $detail.Success -or $detail.Value -notmatch 'shop\.title\(\)' -or $detail.Value -notmatch 'shop\.shopId\(\)') {
    throw 'The selected shop detail screen must display both the visible title and its id.'
}

Write-Host 'Admin shop title display contract OK'
