. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts
$items = Read-Utf8 (Join-Path $root 'copimine-artifacts\items.yml')

Require-Contains $items 'reclaim-policy: LOSS_ONLY' 'Donation catalog entries must declare LOSS_ONLY reclaim policy.'
Require-Regex $artifacts "status='LOST_RECLAIMABLE'" 'Artifacts reclaim flow must read only LOST_RECLAIMABLE instances.'
Require-Regex $artifacts '"LOSS_ONLY"\.equalsIgnoreCase\(this\.firstNonBlank\([^)]*reclaimPolicy\(\)' 'Artifacts must reject reclaim when donation catalog policy is not LOSS_ONLY.'
Require-Regex $artifacts "status='REPLACED_AFTER_LOSS'" 'Artifacts reclaim flow must retire the old instance with REPLACED_AFTER_LOSS.'
Require-Regex $artifacts '"CONSUMED"' 'Artifacts must keep intentional CONSUMED status separate from reclaimable loss.'
$breakHandler = [regex]::Match($artifacts, '(?s)public void onPlayerItemBreak\(PlayerItemBreakEvent var1\).*?(?=\n\s*@EventHandler|\n\s*private |\z)')
if ($breakHandler.Success -and $breakHandler.Value -match '"BROKEN"') {
    $errors.Add('Durability destruction must use the reclaimable loss journal instead of terminal BROKEN status.')
}

Throw-IfErrors 'ValidateCopiMineDonationReclaimLossOnly'
