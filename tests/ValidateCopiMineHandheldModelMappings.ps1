. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\models_manifest.json'
$modelsRoot = Join-Path $root 'resourcepacks\src\assets\minecraft\models\item'

$manifest = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
$expected = @{
  zmei_gorynych = @{ alias = 'zmei_gorinich_sword.json'; artifact = 'zmei_gorynych.json'; parent = 'minecraft:item/handheld' }
  smena_bez_perekura_pickaxe = @{ alias = 'smena_bez_perekura_pickaxe.json'; artifact = 'smena_bez_perekura_pickaxe.json'; parent = 'minecraft:item/handheld' }
  lesnoy_bespredel_axe = @{ alias = 'lesnoy_bespredel_axe.json'; artifact = 'lesnoy_bespredel_axe.json'; parent = 'minecraft:item/handheld' }
  kopatel_transhey_shovel = @{ alias = 'kopatel_transhey_shovel.json'; artifact = 'kopatel_transhey_shovel.json'; parent = 'minecraft:item/handheld' }
  fermer_bez_sna_hoe = @{ alias = 'fermer_bez_sna_hoe.json'; artifact = 'fermer_bez_sna_hoe.json'; parent = 'minecraft:item/handheld' }
  dezhurniy_argument_sword = @{ alias = 'dezhurny_argument_sword.json'; artifact = 'dezhurniy_argument_sword.json'; parent = 'minecraft:item/handheld' }
  vechniy_razgon_firework = @{ alias = 'vechny_razgon_firework_rocket.json'; artifact = 'vechniy_razgon_firework.json'; parent = 'minecraft:item/generated' }
  copimine_miner_pickaxe = @{ alias = 'copiminer_pickaxe.json'; artifact = 'copimine_miner_pickaxe.json'; parent = 'minecraft:item/handheld' }
  craftsman_hammer = @{ alias = 'remeslenick_mace.json'; artifact = 'craftsman_hammer.json'; parent = 'minecraft:item/handheld_mace' }
}

foreach ($entry in $manifest.items) {
  $id = [string]$entry.id
  if (-not $expected.ContainsKey($id)) { continue }
  $modelRef = [string]$entry.model
  $expectedRef = 'copimine:item/artifacts/' + ($expected[$id].artifact -replace '\.json$', '')
  if ($modelRef -ne $expectedRef) { $errors.Add("$id must use $expectedRef, got $modelRef") }
  $path = Join-Path $modelsRoot $expected[$id].alias
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    $errors.Add("Missing handheld model file: $path")
    continue
  }
  $model = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
  if ([string]$model.parent -ne $expected[$id].parent) { $errors.Add("$id has wrong parent: $model.parent") }
  if (-not ([string]$model.textures.layer0).StartsWith('copimine:item/artifacts/')) {
    $errors.Add("$id must use a CopiMine texture namespace, not a missing minecraft:item texture")
  }
  $artifactPath = Join-Path $root ('resourcepacks\\src\\assets\\copimine\\models\\item\\artifacts\\' + $expected[$id].artifact)
  if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
    $errors.Add("Missing artifact model file: $artifactPath")
  } else {
    $artifactModel = Get-Content -Raw -Encoding UTF8 $artifactPath | ConvertFrom-Json
    if ([string]$artifactModel.parent -ne $expected[$id].parent) { $errors.Add("$id artifact model has wrong parent: $artifactModel.parent") }
    if (-not ([string]$artifactModel.textures.layer0).StartsWith('copimine:item/artifacts/')) {
      $errors.Add("$id artifact model must use a CopiMine texture namespace")
    }
  }
}

$compassSource = Get-Content -Raw -Encoding UTF8 (Join-Path $modelsRoot 'gde_moy_lut_blyt_compass.json')
if ($compassSource -match 'time_to_pay_suchki_clock') { $errors.Add('Compass model must not reference the clock frames.') }
if ($compassSource -notmatch 'copimine:item/artifacts/gde_moy_lut_blyat_compass') { $errors.Add('Compass model must use the CopiMine compass texture.') }
$clockSource = Get-Content -Raw -Encoding UTF8 (Join-Path $modelsRoot 'time_to_pay_suchki_clock.json')
if ($clockSource -notmatch 'copimine:item/artifacts/vremya_platit_nalogi_clock') { $errors.Add('Clock model must use the CopiMine clock texture.') }

$zip = Join-Path $root 'resourcepacks\build\CopiMineResourcePack.zip'
if (Test-Path -LiteralPath $zip) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
  try {
    foreach ($path in @(
      'assets/minecraft/models/item/compass.json',
      'assets/minecraft/models/item/clock.json',
      'assets/minecraft/models/item/gde_moy_lut_blyt_compass_directional.json',
      'assets/minecraft/models/item/time_to_pay_suchki_clock_directional.json'
    )) {
      if (-not ($archive.Entries | Where-Object FullName -eq $path)) { $errors.Add("Resource pack is missing $path") }
    }
    foreach ($animation in @(
      @{ path = 'assets/minecraft/models/item/gde_moy_lut_blyt_compass_directional.json'; count = 32; final = 'minecraft:item/gde_moy_lut_blyt_compass_16' },
      @{ path = 'assets/minecraft/models/item/time_to_pay_suchki_clock_directional.json'; count = 64; final = 'minecraft:item/time_to_pay_suchki_clock_00' }
    )) {
      $entry = $archive.Entries | Where-Object FullName -eq $animation.path
      if (-not $entry) { continue }
      $reader = New-Object System.IO.StreamReader($entry.Open())
      try { $directional = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
      if ($directional.overrides.Count -ne ($animation.count + 1)) {
        $errors.Add("$($animation.path) must contain $($animation.count + 1) thresholds")
      } else {
        $last = [string]$directional.overrides[$directional.overrides.Count - 1].model
        if ($last -ne $animation.final) { $errors.Add("$($animation.path) must close the cycle with $($animation.final), got $last") }
        foreach ($override in $directional.overrides) {
          $modelEntry = $archive.Entries | Where-Object FullName -eq ('assets/minecraft/models/' + ([string]$override.model).Split(':', 2)[1] + '.json')
          if (-not $modelEntry) { $errors.Add("$($animation.path) references a missing model: $($override.model)") }
        }
      }
    }
  } finally { $archive.Dispose() }
}

Throw-IfErrors 'ValidateCopiMineHandheldModelMappings'
