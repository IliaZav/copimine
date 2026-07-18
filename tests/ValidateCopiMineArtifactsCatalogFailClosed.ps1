$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$loader = [regex]::Match($source, '(?s)private void loadCatalogFromConfig\(\) \{.*?(?=\r?\n\s*private CopiMineArtifacts\.DonationCatalogItem parseDonationCatalogItem)')
$donation = [regex]::Match($source, '(?s)private CopiMineArtifacts\.DonationCatalogItem parseDonationCatalogItem\(Map<\?, \?> var1\) \{.*?(?=\r?\n\s*private CopiMineArtifacts\.CatalogItem synthesizeDonationRuntimeItem)')

if (-not $loader.Success -or $loader.Value -notmatch 'Duplicate artifact catalog item id' -or $loader.Value -match 'var8 = Material\.STONE') {
    throw 'The AR artifact catalog must reject duplicate IDs and invalid materials instead of overwriting items or issuing stone.'
}

if (-not $donation.Success -or $donation.Value -match 'var3 = Material\.STONE' -or $donation.Value -notmatch 'Invalid donation catalog material') {
    throw 'The donation catalog must reject an invalid base material instead of silently issuing stone.'
}

Write-Host 'Artifact catalog fail-closed contract OK'
