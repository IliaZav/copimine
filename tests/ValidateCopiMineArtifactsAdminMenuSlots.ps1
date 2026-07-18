$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$menu = [regex]::Match($source, '(?s)private void openArtifactsAdminMenu\(Player var1\) \{.*?(?=\r?\n\s*private void openAdminShops)')
if (-not $menu.Success) {
    throw 'Could not locate the artifact administration menu.'
}

if ($menu.Value -notmatch '20,\s*this\.button\(\s*Material\.CLOCK,\s*"&aReload"') {
    throw 'Reload must have its own administration-menu slot.'
}

if ($menu.Value -notmatch '(?s)16,\s*this\.button\(\s*Material\.NETHER_STAR,.*?"donation:root"') {
    throw 'Donation shop must retain its own administration-menu slot.'
}

if ($menu.Value -match '16,\s*this\.button\(\s*Material\.CLOCK,\s*"&aReload"') {
    throw 'Reload and donation shop must not share slot 16.'
}

Write-Host 'Artifact administration menu slot contract OK'
