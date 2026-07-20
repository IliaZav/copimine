$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $root 'copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java'
$source = Get-Content -Raw -LiteralPath $sourcePath

function Get-MethodBody([string]$name, [string]$nextName) {
    $start = $source.IndexOf($name, [StringComparison]::Ordinal)
    if ($start -lt 0) { throw "Method marker not found: $name" }
    $end = $source.IndexOf($nextName, $start + $name.Length, [StringComparison]::Ordinal)
    if ($end -lt 0) { throw "Next method marker not found: $nextName" }
    return $source.Substring($start, $end - $start)
}

$pending = Get-MethodBody 'private void deliverPendingRowV2' 'private void deliverPendingRowLegacy'
$donation = Get-MethodBody 'private void deliverDonationClaimRowV2' 'private CompletableFuture<List<CopiMineArtifacts.DonationClaimRow>> readDonationClaimsAsync'
$reclaim = Get-MethodBody 'private void reclaimDonationItemSafe' 'private CompletableFuture<Boolean> finalizeDonationReclaimAsync'

foreach ($entry in @(@{ Name='pending'; Body=$pending }, @{ Name='donation'; Body=$donation }, @{ Name='reclaim'; Body=$reclaim })) {
    if ($entry.Body -notmatch 'cacheOfficialBinding\(') {
        throw "$($entry.Name) delivery does not bind the delivered item to its owner."
    }
    if ($entry.Body -notmatch 'removeProvisionalDonationInstances\(') {
        throw "$($entry.Name) delivery does not clear provisional state."
    }
}

if ($source -notmatch 'NALOGOVAYA_KOSA.*12\.0|12\.0.*NALOGOVAYA_KOSA') {
    throw 'Kosa damage target is not declared as 12.'
}
if ($source -notmatch 'attackDamageKey') {
    throw 'Custom attack damage attribute support is missing.'
}
if ($source -notmatch 'tryRareArTheft') {
    throw 'Rare AR theft hook is missing.'
}

$economyPath = Join-Path $root 'copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java'
$economy = Get-Content -Raw -LiteralPath $economyPath
if ($economy -notmatch 'Donation purchase requires a linked active AR bank account') {
    throw 'Donation purchase does not enforce an active linked bank account.'
}
if ($economy -notmatch 'stealFromPlayerAccount') {
    throw 'Economy bridge AR theft transaction is missing.'
}

$itemsPath = Join-Path $root 'copimine-artifacts/items.yml'
$items = Get-Content -Raw -LiteralPath $itemsPath
$kosaBlockStart = $items.IndexOf('item-id: kosa_nalogovoy_inspekcii', [StringComparison]::Ordinal)
if ($kosaBlockStart -lt 0) { throw 'Kosa catalog item is missing.' }
$kosaBlock = $items.Substring($kosaBlockStart, [Math]::Min(1000, $items.Length - $kosaBlockStart))
if ($kosaBlock -notmatch '(?m)^\s+proc-chance:\s*0\.30\s*$') {
    throw 'Kosa proc chance is not 30%.'
}

if ($source -notmatch 'random\.nextDouble\(\)\s*>?=\s*0\.001D') {
    throw 'Rare AR theft probability is not 0.1%.'
}
if ($source -notmatch 'var1\.setDamage\(var1\.getDamage\(\) \+ 4\.0\)') {
    throw 'Kosa hit does not steal two hearts of health.'
}

Write-Host 'Artifact delivery binding and combat hardening checks passed.'
