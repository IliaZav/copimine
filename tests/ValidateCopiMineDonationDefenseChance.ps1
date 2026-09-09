$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$text = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @(
  'rollEffectChance(var13)',
  'rollEffectChance(var4)',
  'actionCooldowns.put(this.actionCooldownKey(var2,'
)) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing defense chance/cooldown marker: $marker") }
}
foreach ($marker in @(
  'SHIELD_NAUSEA_PROC_CHANCE',
  'SHIELD_WEAKNESS_PROC_CHANCE',
  'SHIELD_OWNER_BUFF_PROC_CHANCE',
  'shieldLightningCooldowns',
  'random.nextDouble() < SHIELD_NAUSEA_PROC_CHANCE'
)) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing shield defense marker: $marker") }
}
if ($errors.Count) { throw ("Donation defense chance validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineDonationDefenseChance passed.'
