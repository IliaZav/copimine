$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$artifactPath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$artifact = Get-Content -LiteralPath $artifactPath -Raw -Encoding UTF8
$items = Get-Content -LiteralPath (Join-Path $root 'copimine-artifacts\items.yml') -Raw -Encoding UTF8
$server = Get-Content -LiteralPath (Join-Path $root 'minecraft\server\server.properties') -Raw -Encoding UTF8
$install = Get-Content -LiteralPath (Join-Path $root 'deploy\ubuntu\install_release.sh') -Raw -Encoding UTF8
$unpack = Get-Content -LiteralPath (Join-Path $root 'deploy\ubuntu\copimine_unpack_and_verify.sh') -Raw -Encoding UTF8
$backend = Get-Content -LiteralPath (Join-Path $root 'admin-web\backend\main.py') -Raw -Encoding UTF8
$errors = [System.Collections.Generic.List[string]]::new()

function Require([string]$text, [string]$needle, [string]$message) {
    if (-not $text.Contains($needle)) { $errors.Add($message) }
}
function RequireRegex([string]$text, [string]$pattern, [string]$message) {
    if ($text -notmatch $pattern) { $errors.Add($message) }
}

Require $server 'simulation-distance=5' 'server.properties must use simulation distance 5.'
Require $install "'simulation-distance': '5'" 'Ubuntu installer must write simulation distance 5.'
Require $unpack '"simulation-distance": "5"' 'Ubuntu unpacker must write simulation distance 5.'
Require $artifact 'strikeLightning(' 'Combat effects that call for lightning must create a real lightning strike.'
Require $artifact 'REGENERATION' 'Debuff amulet must convert a negative effect into a positive regeneration effect.'
Require $artifact 'STRENGTH' 'Debuff amulet must convert weakness into strength.'
Require $artifact 'SPEED' 'Debuff amulet must convert slowness into speed.'
Require $artifact 'SATURATION' 'Debuff amulet must convert hunger into saturation.'
if ($artifact -match '(?s)case "COLD_FOG":(?:(?!case ").)*?addPotionEffect\(new PotionEffect\(PotionEffectType\.SLOWNESS') {
    $errors.Add('Amulet visual feedback must not add a slowness debuff to its owner.')
}
Require $artifact 'PotionEffectType.NIGHT_VISION' 'Amulet visual feedback must remain a positive/non-debuff effect.'
Require $artifact 'rayTraceBlocks' 'Donation compass must trace the look direction before teleporting.'
Require $artifact '100.0D' 'Donation compass must cap teleport distance at 100 blocks.'
Require $artifact 'teleport(' 'Donation compass must teleport the player, not modify the global vanilla compass target.'
Require $artifact 'getHighestBlockYAt' 'Donation compass must place the player on the surface instead of underground.'
Require $items 'effect-profile-id: NAKOPAL_PICKAXE' 'The NAKOPAL_PICKAXE donation item must remain configured.'
Require $items 'proc-chance: 1.0' 'The NAKOPAL_PICKAXE ability must proc reliably.'
RequireRegex $items '(?s)item-id:\s*batin_remen_sudnogo_dnya.*?proc-chance:\s*1\.0' 'The Batin belt must reliably trigger its lightning ability.'
Require $artifact 'List<Block> placedBlocks' 'The NAKOPAL_PICKAXE trap must track every temporary burial block it places.'
Require $artifact 'preserveDonationItemFromVoid' 'A donation item entering the void must be returned to a safe location instead of removed.'
RequireRegex $artifact '(?s)tickEquippedArmor\(\).*?PotionEffectType\.ABSORPTION' 'The treasurer chestplate must expose a visible passive protection effect while equipped.'
Require $artifact '200L' 'The belt cobweb effect must last ten seconds when it procs.'
Require $artifact '0.20D' 'The belt cobweb effect must have a separate 20 percent chance.'
Require $artifact '600' 'Belt and chestplate buffs must last at least 30 seconds.'
Require $artifact 'INSUFFICIENT_AR' 'AR theft must fall back to inventory when the bank has zero balance.'
Require $artifact 'sendArTheftFailureMessages' 'AR theft must report a failed theft to the attacker and the poor victim.'
RequireRegex $artifact '(?s)OfficialArService ar = this\.officialArService\(\);.*?ar\.removeAmount\(victim\.getInventory\(\), 1\)' 'Inventory theft must remove exactly one official AR from the victim inventory.'
Require $artifact 'reset_at > 100000000000' 'Purchase-limit queries must normalize millisecond reset timestamps.'
Require $backend 'class AdminArtifactLimitResetIn' 'The reset endpoint must accept a wildcard item id.'
Require $backend 'requested_id in {"*", "all"}' 'The reset endpoint must handle reset-all explicitly.'
Require $backend "RESET_RETIRED" 'Resetting a donation limit must retire the previous live entitlement so the database unique guard cannot block a new purchase.'
Require $backend 'artifactLimitResets' 'Player detail responses must expose the effective artifact reset timestamps.'
Require $artifact 'artifact_purchase_limit_resets' 'The game-side donation entitlement guard must read administrator reset timestamps.'
RequireRegex (Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\admin-web\frontend\assets\js\cabinet-runtime.js') -Raw -Encoding UTF8) 'artifactLimitResets' 'The player card must calculate limits after the latest reset.'
RequireRegex $artifact '(?s)tryRareArTheft\(Player attacker, LivingEntity target\).*?AR_THEFT_PROC_CHANCE' 'AR theft must use a named proc chance and remain testable.'
RequireRegex $artifact '(?s)tryRareArTheft\(Player attacker, LivingEntity target\).*?NO_BANK_ACCOUNT.*?INSUFFICIENT_AR' 'AR theft must fall back to inventory when the bank is missing or empty.'
Require $artifact 'reconcileDonationLossJournal' 'Void/loss handling must reconcile the journal before reclaiming.'
Require $artifact 'Instant.ofEpochMilli(expiresAt)' 'Tax subscription delivery must show the super-citizen message.'
Require $artifact 'tickEquippedArmor' 'Treasurer chestplate must refresh its armor effect while equipped.'
Require $artifact 'PotionEffectType.RESISTANCE, 60' 'Treasurer chestplate must provide a visible resistance effect while equipped.'

if ($errors.Count -gt 0) {
    throw ('ValidateCopiMineRequestedArtifactFixes failed:' + "`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineRequestedArtifactFixes passed.'
