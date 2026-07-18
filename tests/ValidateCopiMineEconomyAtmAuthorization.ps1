$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$sourcePath = Join-Path $root 'copimine-economy-core\src\me\copimine\economycore\CopiMineEconomyCore.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$errors = [System.Collections.Generic.List[string]]::new()

function Require-Guard([string]$signature, [string]$nextSignature, [string]$label) {
    $pattern = "(?s)" + [regex]::Escape($signature) + ".*?(?=" + [regex]::Escape($nextSignature) + ")"
    $match = [regex]::Match($source, $pattern)
    if (-not $match.Success) {
        $errors.Add("Could not locate $label.")
        return
    }
    if ($match.Value -notmatch [regex]::Escape('if (!requireEconomyAdmin(')) {
        $errors.Add("$label must reject a non-economy-admin before it can reach an ATM-management operation.")
    }
}

if ($source -notmatch [regex]::Escape('private boolean requireEconomyAdmin(Player player)')) {
    $errors.Add('A shared economy-admin denial helper is required for the ATM-management boundary.')
}

Require-Guard 'public void openAtmDirectory(Player player) throws Exception {' '    private BlockKey blockKey(Block block)' 'public ATM directory wrapper'
Require-Guard 'public String createAtmFromTarget(Player player) throws Exception {' '    public String archiveAtm(Player actor, String atmId) throws Exception {' 'public ATM creation wrapper'
Require-Guard 'public String archiveAtm(Player actor, String atmId) throws Exception {' '    @EventHandler' 'public ATM archive wrapper'
Require-Guard 'private void openBankAtms(Player player) throws Exception {' '    private String createBankAtmFromTarget(Player player) throws Exception {' 'ATM directory sink'
Require-Guard 'private String createBankAtmFromTarget(Player player) throws Exception {' '    private String createBankAtmFromTargetAsync(Player player) throws Exception {' 'synchronous ATM creation sink'
Require-Guard 'private String createBankAtmFromTargetAsync(Player player) throws Exception {' '    private String archiveBankAtm(Player actor, String atmId) throws Exception {' 'asynchronous ATM creation sink'
Require-Guard 'private String archiveBankAtm(Player actor, String atmId) throws Exception {' '    private String archiveBankAtmAsync(Player actor, String atmId) throws Exception {' 'synchronous ATM archive sink'
Require-Guard 'private String archiveBankAtmAsync(Player actor, String atmId) throws Exception {' '    private void openBankAtm(Player player, String atmId)' 'asynchronous ATM archive sink'
Require-Guard 'private void openAtmDeleteConfirm(Player player, String atmId) {' '    private void openEconomySummary(Player player) throws Exception {' 'ATM delete-confirmation screen'

$menu = [regex]::Match($source, '(?s)private void handleMenuAction\(Player player, MenuHolder menu, String action\) throws Exception \{.*?(?=\r?\n\s*private void openAtmDeleteConfirm)')
if (-not $menu.Success) {
    $errors.Add('Could not locate ATM menu action handling.')
} else {
    foreach ($action in @('economy:atms', 'atm:create-target', 'atm:delete-confirm:', 'atm:delete:', 'target.equals("atms")')) {
        if ($menu.Value -notmatch [regex]::Escape($action)) {
            $errors.Add("ATM menu action path missing from audit: $action")
        }
    }
    if ($menu.Value -notmatch [regex]::Escape('requireEconomyAdmin(player)')) {
        $errors.Add('ATM menu management actions must perform an explicit authorization check before invoking their sinks.')
    }
}

$service = [regex]::Match($source, '(?s)private final class AtmServiceImpl implements AtmService \{.*?(?=\r?\n\s*private final class ArtifactsBridgeImpl)')
if (-not $service.Success) {
    $errors.Add('Could not locate the public ATM service wrapper.')
} else {
    foreach ($method in @('openAtmDirectory(Player player)', 'createAtTarget(Player player)', 'archive(Player actor, String atmId)')) {
        $index = $service.Value.IndexOf($method)
        if ($index -lt 0 -or $service.Value.Substring($index, [Math]::Min(360, $service.Value.Length - $index)) -notmatch [regex]::Escape('requireEconomyAdmin(')) {
            $errors.Add("ATM service method $method must independently reject a non-economy-admin.")
        }
    }
}

$normalBank = [regex]::Match($source, '(?s)private void openBankAtm\(Player player, String atmId\) \{.*?(?=\r?\n\s*private void openBankAtmAccount)')
if (-not $normalBank.Success -or $normalBank.Value -match [regex]::Escape('requireEconomyAdmin(player)')) {
    $errors.Add('Normal players must retain access to the personal ATM banking flow; only ATM management may be restricted.')
}

if ($errors.Count -gt 0) {
    throw ("ATM authorization validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ATM authorization validation passed.'
