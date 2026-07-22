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

if ($method.Value -notmatch 'Particle\.ELECTRIC_SPARK') {
  throw 'A successful shield defence must have a visible in-world effect.'
}
if ($method.Value -notmatch 'strikeLightningEffect\(') {
  throw 'A successful shield defence must show the configured lightning effect to the attacker.'
}
if ($method.Value -notmatch 'PotionEffectType\.NAUSEA' -or $method.Value -notmatch 'PotionEffectType\.WEAKNESS') {
  throw 'A successful shield defence must apply the configured attacker debuffs.'
}

Write-Host 'Shield defence contract OK'
