$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$text = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @(
  'rollEffectChance(var13)',
  'rollEffectChance(var4)',
  'random.nextDouble() < SHIELD_NAUSEA_PROC_CHANCE',
  'random.nextDouble() < SHIELD_WEAKNESS_PROC_CHANCE',
  'random.nextDouble() < SHIELD_OWNER_BUFF_PROC_CHANCE',
  'shieldLightningCooldowns.put(var2.getUniqueId(), current + SHIELD_LIGHTNING_COOLDOWN_SECONDS)',
  'actionCooldowns.put(this.actionCooldownKey(var2,'
)) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing defense chance/cooldown marker: $marker") }
}
if ($errors.Count) { throw ("Donation defense chance validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineDonationDefenseChance passed.'
