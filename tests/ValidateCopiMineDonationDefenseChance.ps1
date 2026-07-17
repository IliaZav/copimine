$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$text = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @(
  'rollEffectChance(var13)',
  'rollEffectChance(var4)',
  'rollEffectChance(var5)',
  'actionCooldowns.put(var2.getUniqueId()'
)) {
  if ($text -notmatch [regex]::Escape($marker)) { $errors.Add("Missing defense chance/cooldown marker: $marker") }
}
if ($errors.Count) { throw ("Donation defense chance validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMineDonationDefenseChance passed.'
