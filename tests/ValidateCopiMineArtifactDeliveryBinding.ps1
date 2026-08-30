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
$items = Get-Content -Raw -Encoding UTF8 -LiteralPath $itemsPath
$kosaBlockStart = $items.IndexOf('item-id: kosa_nalogovoy_inspekcii', [StringComparison]::Ordinal)
if ($kosaBlockStart -lt 0) { throw 'Kosa catalog item is missing.' }
$kosaBlock = $items.Substring($kosaBlockStart, [Math]::Min(1000, $items.Length - $kosaBlockStart))
if ($kosaBlock -notmatch '(?m)^\s+cooldown-seconds:\s*0\s*$' -or $kosaBlock -notmatch '2\.5%.*1.?3 AR') {
    throw 'Kosa must have no item cooldown and must describe the 2.5% 1-3 AR theft range.'
}

if ($source -notmatch 'AR_THEFT_PROC_CHANCE\s*=\s*0\.025D' -or $source -notmatch 'random\.nextDouble\(\)\s*>?=\s*AR_THEFT_PROC_CHANCE' -or $source -notmatch '1 \+ random\.nextInt\(3\)') {
    throw 'Rare AR theft probability is not 2.5% with a 1-3 AR amount.'
}
if ($source -notmatch 'KOSA_HEALTH_PROC_CHANCE\s*=\s*0\.30D' -or $source -notmatch 'KOSA_HUNGER_PROC_CHANCE\s*=\s*0\.20D' -or $source -notmatch 'KOSA_WITHER_PROC_CHANCE\s*=\s*0\.20D' -or $source -notmatch 'KOSA_BLINDNESS_PROC_CHANCE\s*=\s*0\.10D' -or $source -notmatch 'PotionEffectType\.HUNGER, 40, 0' -or $source -notmatch 'PotionEffectType\.BLINDNESS, 140, 2') {
    throw 'Kosa independent health, hunger, wither and blindness effects are missing.'
}

Write-Host 'Artifact delivery binding and combat hardening checks passed.'
