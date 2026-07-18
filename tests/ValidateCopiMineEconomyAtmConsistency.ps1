$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($marker in @(
    'CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_atms_location_active ON ar_atms(world,x,y,z) WHERE active=1',
    'ON CONFLICT (world,x,y,z) WHERE active=1 DO NOTHING',
    'archiveDuplicateActiveAtms('
)) {
    if ($source -notmatch [regex]::Escape($marker)) {
        $errors.Add("ATM creation must have a runtime-safe uniqueness guard (missing: $marker).")
    }
}

$asyncCreate = [regex]::Match($source, '(?s)private String createBankAtmFromTargetAsync\(Player player\).*?(?=\r?\n\s*private String archiveBankAtm)')
if (-not $asyncCreate.Success -or $asyncCreate.Value -notmatch 'ON CONFLICT \(world,x,y,z\) WHERE active=1 DO NOTHING') {
    $errors.Add('The asynchronous ATM creation path must use the database uniqueness constraint, not a racy read-then-insert check.')
}

$settlements = [regex]::Match($source, '(?s)private void processPendingArSettlements\(Player player, boolean notifyNoSpace\).*?(?=\r?\n\s*private List<PendingArSettlement> loadPendingArSettlements)')
if (-not $settlements.Success -or $settlements.Value -notmatch '(?s)if \(!player\.isOnline\(\)\) \{\s*dbAsync\("pending ar settlements release", \(\) -> releasePendingArSettlements\(ids\)\);\s*return;') {
    $errors.Add('If a player disconnects after pending AR is reserved but before issuance, the settlement must be returned to PENDING instead of being stranded in DELIVERING.')
}

if ($source -notmatch [regex]::Escape('quarantineInterruptedPendingArSettlements()')) {
    $errors.Add('Startup must quarantine pre-existing interrupted pending-AR deliveries so a server crash cannot cause an automatic duplicate issue.')
}

if ($errors.Count -gt 0) {
    throw ("Economy ATM consistency validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Economy ATM consistency validation passed.'
