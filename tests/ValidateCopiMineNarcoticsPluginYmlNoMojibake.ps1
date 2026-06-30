$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginYml = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-narcotics\plugin.yml')
foreach ($marker in @('Ð','Ñ','пїЅ')) {
  if ($pluginYml.Contains($marker)) {
    throw 'copimine-narcotics/plugin.yml still contains mojibake.'
  }
}
Write-Host 'ValidateCopiMineNarcoticsPluginYmlNoMojibake passed.'
