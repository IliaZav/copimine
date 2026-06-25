$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$manifest = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\src\assets\copimine\manifests\narcotics_items_manifest.json')
$config = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\config.yml')
$models = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'resourcepacks\models_manifest.json')
foreach ($pair in @(
  @{ Id = 'feta'; Cmd = '810001' },
  @{ Id = 'kola'; Cmd = '810002' },
  @{ Id = 'girion'; Cmd = '810003' },
  @{ Id = 'sbp'; Cmd = '810004' },
  @{ Id = 'sos'; Cmd = '810005' },
  @{ Id = 'drun'; Cmd = '810006' },
  @{ Id = 'chups'; Cmd = '810007' },
  @{ Id = 'borshevik'; Cmd = '810008' },
  @{ Id = 'zhuzevo'; Cmd = '810009' }
)) {
  if ($manifest -notmatch [regex]::Escape('"id": "' + $pair.Id + '"')) { throw "Manifest missing narcotics item id: $($pair.Id)" }
  if ($manifest -notmatch [regex]::Escape('"custom_model_data": ' + $pair.Cmd)) { throw "Manifest missing CMD for $($pair.Id): $($pair.Cmd)" }
  if ($config -notmatch [regex]::Escape('custom_model_data: ' + $pair.Cmd)) { throw "Config missing CMD for $($pair.Id): $($pair.Cmd)" }
  if ($models -notmatch [regex]::Escape('"id": "' + $pair.Id + '"')) { throw "Combined models manifest missing id: $($pair.Id)" }
}
Write-Host 'Narcotics resource pack manifest matches Java/config mappings.'
