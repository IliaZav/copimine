$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $PSScriptRoot '..\copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$method = [regex]::Match($source, '(?s)public void onArtifactDefend\(EntityDamageEvent var1\) \{.*?(?=\r?\n\s*@EventHandler)')

if (-not $method.Success) {
    throw 'Could not locate artifact defence handler.'
}

if ($method.Value -notmatch 'getItemInMainHand\(\), var2, "defend_mainhand"') {
    throw 'The shield defence must recognize an official shield held in the main hand.'
}

if ($method.Value -notmatch 'getItemInOffHand\(\), var2, "defend_offhand"') {
    throw 'The shield defence must recognize an official shield held in the off hand.'
}

if ($method.Value -notmatch 'attacker\.getWorld\(\)\.strikeLightning\(attacker\.getLocation\(\)\)') {
  throw 'A successful shield defence must create a real lightning strike on the attacker.'
}
if ($method.Value -match 'strikeLightningEffect\(') {
  throw 'Shield defence must not use the visual-only lightning effect.'
}
if ($method.Value -notmatch 'PotionEffectType\.NAUSEA' -or $method.Value -notmatch 'PotionEffectType\.WEAKNESS') {
  throw 'A successful shield defence must apply the configured attacker debuffs.'
}
if ($method.Value -notmatch 'SHIELD_NAUSEA_PROC_CHANCE' -or $method.Value -notmatch 'SHIELD_WEAKNESS_PROC_CHANCE') {
  throw 'Shield attacker debuffs must use independent chance constants.'
}
if ($method.Value -notmatch 'SHIELD_LIGHTNING_COOLDOWN_SECONDS' -or $method.Value -notmatch 'current \+ SHIELD_LIGHTNING_COOLDOWN_SECONDS') {
  throw 'Shield lightning must use the dedicated 20-second cooldown.'
}
if ($method.Value -notmatch 'SHIELD_OWNER_BUFF_PROC_CHANCE' -or $method.Value -notmatch 'PotionEffectType\.REGENERATION' -or $method.Value -notmatch 'PotionEffectType\.SPEED') {
  throw 'Shield owner buff chance must grant Regeneration II and Speed I.'
}
Write-Host 'Shield defence contract OK'
