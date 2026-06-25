$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$srcManifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_items_manifest.json')
$combinedManifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\models_manifest.json')
foreach ($pair in @(
  @{ Id='feta'; Cmd='810001'; Material='WHITE_DYE' },
  @{ Id='kola'; Cmd='810002'; Material='SUGAR' },
  @{ Id='girion'; Cmd='810003'; Material='SLIME_BALL' },
  @{ Id='sbp'; Cmd='810004'; Material='GOLD_NUGGET' },
  @{ Id='sos'; Cmd='810005'; Material='BONE_MEAL' },
  @{ Id='drun'; Cmd='810006'; Material='PAPER' },
  @{ Id='chups'; Cmd='810007'; Material='BLUE_STAINED_GLASS_PANE' },
  @{ Id='borshevik'; Cmd='810008'; Material='KELP' },
  @{ Id='zhuzevo'; Cmd='810009'; Material='BROWN_DYE' }
)) {
  foreach ($marker in @(
    "custom_model_data: $($pair.Cmd)",
    """id"": ""$($pair.Id)""",
    """custom_model_data"": $($pair.Cmd)",
    """base_material"": ""$($pair.Material)"""
  )) {
    if (($config + $srcManifest + $combinedManifest) -notmatch [regex]::Escape($marker)) {
      throw "Manifest/Java alignment marker missing for $($pair.Id): $marker"
    }
  }
}
Write-Host 'Resource pack narcotics manifest alignment validation passed.'
