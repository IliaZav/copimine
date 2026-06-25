$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifestPath = Join-Path $root 'resourcepacks\models_manifest.json'
$artifactsJavaPath = Join-Path $root 'copimine-artifacts\src\me\copimine\artifacts\CopiMineArtifacts.java'
$narcoticsConfigPath = Join-Path $root 'copimine-narcotics\config.yml'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in @($manifestPath,$artifactsJavaPath,$narcoticsConfigPath)) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing manifest alignment file: $path") }
}

if ((Test-Path $manifestPath) -and (Test-Path $artifactsJavaPath) -and (Test-Path $narcoticsConfigPath)) {
  $manifest = Get-Content -Raw -Encoding UTF8 $manifestPath
  $artifactsJava = Get-Content -Raw -Encoding UTF8 $artifactsJavaPath
  $narcoticsConfig = Get-Content -Raw -Encoding UTF8 $narcoticsConfigPath

  $pairs = @(
    @{ Id = 'zmei_gorynych'; Cmd = '10001'; Material = 'netherite_sword' },
    @{ Id = 'feta'; Cmd = '810001'; Material = 'white_dye' },
    @{ Id = 'kola'; Cmd = '810002'; Material = 'sugar' },
    @{ Id = 'girion'; Cmd = '810003'; Material = 'slime_ball' },
    @{ Id = 'sbp'; Cmd = '810004'; Material = 'gold_nugget' },
    @{ Id = 'sos'; Cmd = '810005'; Material = 'bone_meal' },
    @{ Id = 'drun'; Cmd = '810006'; Material = 'paper' },
    @{ Id = 'chups'; Cmd = '810007'; Material = 'blue_stained_glass_pane' },
    @{ Id = 'borshevik'; Cmd = '810008'; Material = 'kelp' },
    @{ Id = 'zhuzevo'; Cmd = '810009'; Material = 'brown_dye' }
  )

  foreach ($pair in $pairs) {
    foreach ($marker in @(
      """id"": ""$($pair.Id)""",
      """custom_model_data"": $($pair.Cmd)",
      """base_material"": ""$($pair.Material)"""
    )) {
      if ($manifest -notmatch [regex]::Escape($marker)) { $errors.Add("Manifest missing marker for $($pair.Id): $marker") }
    }
  }

  if (($artifactsJava -notmatch [regex]::Escape('ARTIFACT_MODEL_DATA = Map.of(')) -or ($artifactsJava -notmatch [regex]::Escape('"zmei_gorynych", 10001'))) {
    $errors.Add('Artifacts Java must map zmei_gorynych to custom model data 10001.')
  }

  foreach ($marker in @('custom_model_data: 810001','custom_model_data: 810002','custom_model_data: 810003','custom_model_data: 810004','custom_model_data: 810005','custom_model_data: 810006','custom_model_data: 810007','custom_model_data: 810008','custom_model_data: 810009')) {
    if ($narcoticsConfig -notmatch [regex]::Escape($marker)) { $errors.Add("Narcotics config missing manifest-aligned marker: $marker") }
  }
}

if ($errors.Count -gt 0) {
  throw ("Resource pack manifest alignment validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'Resource pack manifest alignment validation passed: Java registries, narcotics config, and manifest use the same custom model data.'
