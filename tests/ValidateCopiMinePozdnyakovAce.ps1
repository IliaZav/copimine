$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$java = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java')
$items = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\items.yml')
$plugin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-artifacts\plugin.yml')
$catalog = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'admin-web\backend\commerce_catalog.py')
$errors = [System.Collections.Generic.List[string]]::new()
foreach ($marker in @(
  'id: kozyrny_tuz_pozdnyakova',
  'material: NETHERITE_LEGGINGS',
  'source: ADMIN_ONLY',
  'effect: POZDNYAKOV_ACE',
  'Enchantment.THORNS, 3, true',
  'Material.LAVA',
  'Material.MAGMA_BLOCK',
  'PotionEffectType.NAUSEA, 100, 2',
  'current + 30L',
  'persistAdminGrantedInstance',
  'copimine.artifacts.admin.give',
  'isAdminOnlyCatalogItem'
)) {
  if ($java -notmatch [regex]::Escape($marker) -and $items -notmatch [regex]::Escape($marker) -and $plugin -notmatch [regex]::Escape($marker) -and $catalog -notmatch [regex]::Escape($marker)) {
    $errors.Add("Pozdnyakov ace marker is missing: $marker")
  }
}
foreach ($marker in @(
  'POZDNYAKOV_LAVA_RADIUS = 2',
  'POZDNYAKOV_LAVA_RESTORE_TICKS = 100L',
  'current + 30L',
  'Material.MAGMA_BLOCK',
  'originalType == Material.LAVA',
  'block.getType() == Material.MAGMA_BLOCK'
)) {
  if ($java -notmatch [regex]::Escape($marker)) { $errors.Add("Pozdnyakov ace safety marker is missing: $marker") }
}
$aceBlock = [regex]::Match($items, '(?s)- id: kozyrny_tuz_pozdnyakova.*?(?=\r?\n\s+- id: eternal_totem|\r?\ndonation-catalog:)').Value
if ($aceBlock -notmatch 'custom_model_data:\s*[1-9][0-9]*') { $errors.Add('Pozdnyakov ace must have positive custom model data.') }
$approvedPhrase = ([string][char]0x0437 + [char]0x0430 + ' ' + [char]0x043F + [char]0x043E + [char]0x043C + [char]0x043E + [char]0x0449 + [char]0x044C + ' ' + [char]0x0432 + ' ' + [char]0x0440 + [char]0x0430 + [char]0x0437 + [char]0x0432 + [char]0x0438 + [char]0x0442 + [char]0x0438 + [char]0x0438 + ' ' + [char]0x043F + [char]0x0440 + [char]0x043E + [char]0x0435 + [char]0x043A + [char]0x0442 + [char]0x0430 + ' CopiMine')
if ($aceBlock -notmatch [regex]::Escape($approvedPhrase)) { $errors.Add('Pozdnyakov ace must use the approved development-credit lore.') }
if ($java -notmatch '"admin"\.equalsIgnoreCase\(var4\[0\]\)[\s\S]*"give"\.equalsIgnoreCase\(var4\[1\]\)') { $errors.Add('Admin give command is not wired under /cmartifacts admin give.') }
if ($java -notmatch 'adminOnlyCatalogItems\.add\(var6\.toLowerCase\(Locale\.ROOT\)\)') { $errors.Add('ADMIN_ONLY catalog items must be kept out of shop categories.') }
if ($catalog -notmatch 'entry\.get\("source"\)[\s\S]*ADMIN_ONLY') { $errors.Add('ADMIN_ONLY catalog items must be excluded from web AR shop output.') }
$loreLines = [regex]::Matches($aceBlock, '(?m)^\s+-\s+"[^"]+"\s*$')
$approvedLore = '&7' + [string][char]0x0437 + [char]0x0430 + ' ' + [char]0x043F + [char]0x043E + [char]0x043C + [char]0x043E + [char]0x0449 + [char]0x044C + ' ' + [char]0x0432 + ' ' + [char]0x0440 + [char]0x0430 + [char]0x0437 + [char]0x0432 + [char]0x0438 + [char]0x0442 + [char]0x0438 + [char]0x0438 + ' ' + [char]0x043F + [char]0x0440 + [char]0x043E + [char]0x0435 + [char]0x043A + [char]0x0442 + [char]0x0430 + ' CopiMine'
if ($loreLines.Count -ne 1 -or $aceBlock -notmatch [regex]::Escape($approvedLore)) { $errors.Add('Pozdnyakov ace lore must contain only the approved credit phrase.') }
if ($errors.Count) { throw ("Pozdnyakov ace validation failed:`n - " + ($errors -join "`n - ")) }
Write-Host 'ValidateCopiMinePozdnyakovAce passed.'
