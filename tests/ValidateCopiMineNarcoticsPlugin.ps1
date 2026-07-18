$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$pluginRoot = Join-Path $root 'copimine-narcotics'
$pluginYml = Join-Path $pluginRoot 'plugin.yml'
$config = Join-Path $pluginRoot 'config.yml'
$jar = Join-Path $root 'minecraft\server\plugins\CopiMineNarcotics.jar'
$serverConfig = Join-Path $root 'minecraft\server\plugins\CopiMineNarcotics\config.yml'
$errors = [System.Collections.Generic.List[string]]::new()

$requiredFiles = @(
  $pluginYml,
  $config,
  (Join-Path $pluginRoot 'src\me\copimine\narcotics\CopiMineNarcotics.java'),
  (Join-Path $pluginRoot 'src\me\copimine\narcotics\db\NarcoticsDatabase.java'),
  (Join-Path $pluginRoot 'src\me\copimine\narcotics\cauldron\CauldronBrewingService.java'),
  (Join-Path $pluginRoot 'src\me\copimine\narcotics\item\NarcoticItemFactory.java'),
  (Join-Path $pluginRoot 'src\me\copimine\narcotics\use\OverdoseService.java'),
  (Join-Path $pluginRoot 'src\me\copimine\visualruntime\VisualRuntimeService.java'),
  $jar,
  $serverConfig
)
foreach ($path in $requiredFiles) {
  if (-not (Test-Path -LiteralPath $path)) { $errors.Add("Missing Phase 1 narcotics file: $path") }
}

if (Test-Path $pluginYml) {
  $yaml = Get-Content -Raw -Encoding UTF8 $pluginYml
  foreach ($marker in @(
    "name: CopiMineNarcotics",
    "version: '2.1.0-client-bridge'",
    "cmnarcotics:",
    "copimine.narcotics.admin",
    "copimine.narcotics.reset",
    "copimine.narcotics.visuals",
    "copimine.narcotics.selfcheck",
    "copimine.narcotics.clearoverdose",
    "cmclient:"
  )) {
    if ($yaml -notmatch [regex]::Escape($marker)) { $errors.Add("plugin.yml missing marker: $marker") }
  }
}

if (Test-Path $config) {
  $cfg = Get-Content -Raw -Encoding UTF8 $config
  foreach ($marker in @(
    "textures:",
    "mode: CUSTOM",
    "client_bridge:",
    "visuals:",
    "enabled: true",
    "mode: AUTO",
    "allow_client_mod_visuals: true",
    "allow_server_particle_fallback: true",
    "feta:",
    "kola:",
    "girion:",
    "sbp:",
    "sos:",
    "drun:",
    "chups:",
    "borshevik:",
    "zhuzevo:",
    "custom_model_data: 810001",
    "custom_model_data: 810009"
  )) {
    if ($cfg -notmatch [regex]::Escape($marker)) { $errors.Add("config missing Phase 1 marker: $marker") }
  }
}

if ((Test-Path $config) -and (Test-Path $serverConfig)) {
  $srcHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $config).Hash
  $serverHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $serverConfig).Hash
  if ($srcHash -ne $serverHash) { $errors.Add('Server narcotics config is not synchronized with release config.') }
}

if (Test-Path $jar) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
  try {
    foreach ($entry in @('plugin.yml','config.yml','me/copimine/narcotics/CopiMineNarcotics.class')) {
      if (-not ($zip.Entries | Where-Object { $_.FullName -eq $entry })) {
        $errors.Add("Active CopiMineNarcotics.jar must embed $entry.")
      }
    }
  } finally {
    $zip.Dispose()
  }
}

if ($errors.Count -gt 0) {
  throw ("CopiMineNarcotics Phase 1 validation failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'CopiMineNarcotics Phase 1 validation passed.'
