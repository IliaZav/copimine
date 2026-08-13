$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$source = Get-Content -LiteralPath (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java') -Raw -Encoding UTF8
$items = Get-Content -LiteralPath (Join-Path $root 'copimine-artifacts\items.yml') -Raw -Encoding UTF8
$block = [regex]::Match($items, '(?ms)^    - item-id: gde_moy_lut_blyat_compass\r?\n.*?(?=^    - item-id:|\z)')

if (-not $block.Success -or $block.Value -notmatch '(?m)^      enabled:\s*false\s*$') {
    throw 'The legacy compass must remain as a disabled catalog tombstone.'
}
foreach ($forbidden in @('activateLootCompass', 'COMPASS_COOLDOWN_SECONDS', 'MAX_COMPASS_TELEPORT_DISTANCE', 'keyCompassCooldownUntil', 'case "LOOT_COMPASS"')) {
    if ($source.Contains($forbidden)) { throw "Retired compass runtime marker is still active: $forbidden" }
}
if ($source -notmatch 'isRetiredArtifact' -or $source -notmatch 'gde_moy_lut_blyat_compass') {
    throw 'The retired compass guard/tombstone synchronization is missing.'
}

Write-Host 'Retired compass contract OK'
